package com.student.demo;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Measures the demo shop before and after the cache, from the terminal.
 *
 * It makes a list of pages to visit (the home page, the 11 product lists and
 * some product pages picked at random, different on every run), then a random
 * visiting order. It clears the cache, visits the pages in that order with
 * cache=off ("without"), then in the same order with the cache ("with"), and
 * prints both sets of numbers side by side. A few "users" (plain threads) send
 * requests at the same time. The times are measured here, in this program: from
 * sending a request until its whole answer has arrived.
 *
 * Nothing is hidden: the "with" run starts with an empty cache, so the first
 * visit to each page is a slow MISS, and it is counted like every other request.
 *
 * Usage: java -cp target/mini-redis-1.0.jar com.student.demo.Benchmark
 *            [baseUrl, default http://localhost:8080] [requests, default 200]
 *            [users, default 4, max 8] [productPages, default 20, max 200]
 */
public class Benchmark {

    static final int MAX_USERS = 8;

    public static void main(String[] args) throws Exception {
        String baseUrl = args.length > 0 ? args[0] : "http://localhost:8080";
        int requests = args.length > 1 ? Integer.parseInt(args[1]) : 200;
        int users = args.length > 2 ? Integer.parseInt(args[2]) : 4;
        int productPages = args.length > 3 ? Integer.parseInt(args[3]) : 20;
        if (requests < 1 || users < 1 || productPages < 0) {
            System.err.println("requests and users must be 1 or more, productPages 0 or more");
            return;
        }
        if (users > MAX_USERS) {
            System.out.println("Using " + MAX_USERS + " users (the most this benchmark allows) instead of " + users);
            users = MAX_USERS;
        }
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        Random random = new Random(); // no seed: every run picks different pages
        List<String> pages = choosePages(productPages, random);
        String[] visits = new String[requests];
        for (int i = 0; i < requests; i++) {
            visits[i] = pages.get(random.nextInt(pages.size()));
        }

        // If we give HttpClient no pool, it makes its own cached (unlimited) one. A small fixed
        // pool keeps the laptop light. Two threads are enough: they only do the client's short
        // background steps, while each user thread waits inside send().
        ExecutorService clientThreads = Executors.newFixedThreadPool(2);
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .executor(clientThreads)
                .build();
        try {
            System.out.println("Benchmark of " + baseUrl + ": " + requests + " requests per run, "
                    + users + " users at the same time");
            System.out.println("Pages in this run: " + pages.size() + " different ones (home page, "
                    + (SalesData.categoryNames().length + 1) + " product lists, "
                    + (pages.size() - SalesData.categoryNames().length - 2) + " product pages picked at random)");
            if (!clearCache(client, baseUrl)) {
                System.out.println("Start DemoApp first, then run the benchmark again.");
                return;
            }

            System.out.println("Run 1 of 2: without Redis (cache=off) ...");
            RunResult without = run(client, baseUrl, visits, "&cache=off", users);
            System.out.println("Run 2 of 2: with Redis (cache=on) ...");
            RunResult with = run(client, baseUrl, visits, "&cache=on", users);

            printTable(without, with, countDifferent(visits));
        } finally {
            clientThreads.shutdown();
        }
    }

    /**
     * The pages a run can visit: the home page, every product list, and the given
     * number of different product pages, picked at random from the 200 products.
     * Every address already contains a "?", so "&cache=off" can be added at the end.
     */
    static List<String> choosePages(int productPages, Random random) {
        List<String> pages = new ArrayList<>();
        pages.add("/api/home?page=home");
        pages.add("/api/products?category=" + ShopQueries.ALL);
        for (String category : SalesData.categoryNames()) {
            pages.add("/api/products?category=" + ProductStats.slug(category));
        }

        int productCount = SalesData.productNames().length;
        List<Integer> ids = new ArrayList<>();
        for (int id = 1; id <= productCount; id++) {
            ids.add(id);
        }
        for (int i = 0; i < Math.min(productPages, productCount); i++) {
            int pick = random.nextInt(ids.size());
            pages.add("/api/product?id=" + ids.remove(pick)); // remove, so no product is picked twice
        }
        return pages;
    }

    /** How many different pages the visiting order really contains. */
    static int countDifferent(String[] visits) {
        List<String> seen = new ArrayList<>();
        for (String visit : visits) {
            if (!seen.contains(visit)) {
                seen.add(visit);
            }
        }
        return seen.size();
    }

    /**
     * Empties the cache first, so the "with" run starts from nothing.
     * Returns false if the website itself could not be reached.
     */
    private static boolean clearCache(HttpClient client, String baseUrl) throws InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/clear-cache"))
                .timeout(Duration.ofSeconds(30)).GET().build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                System.out.println("WARNING: the cache was not cleared (is mini-redis running?): " + response.body());
            }
            return true;
        } catch (IOException e) {
            System.out.println("Could not reach " + baseUrl + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Sends the requests using the given number of user threads and waits for all of them.
     * User 0 sends requests 0, users, 2*users ...; user 1 sends 1, users+1 ... and so on.
     * So every request has its own slot in the result arrays and no two threads share a slot.
     */
    private static RunResult run(HttpClient client, String baseUrl, String[] visits, String cacheChoice,
                                 int users) throws InterruptedException {
        int requests = visits.length;
        RunResult result = new RunResult(requests);

        Thread[] threads = new Thread[users];
        long start = System.nanoTime();
        for (int u = 0; u < users; u++) {
            int firstRequest = u;
            threads[u] = new Thread(() -> {
                for (int i = firstRequest; i < requests; i += users) {
                    sendOne(client, baseUrl + visits[i] + cacheChoice, result, i);
                }
            });
            threads[u].start();
        }
        for (Thread thread : threads) {
            thread.join(); // wait until this user has finished
        }
        result.totalNanos = System.nanoTime() - start;
        return result;
    }

    /** Sends one request and writes its time and X-Cache value into slot i of the result. */
    private static void sendOne(HttpClient client, String url, RunResult result, int i) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30)).GET().build();
        long start = System.nanoTime();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            result.nanos[i] = System.nanoTime() - start;
            if (response.statusCode() == 200) {
                result.cacheState[i] = response.headers().firstValue("X-Cache").orElse("?");
            }
        } catch (IOException e) {
            // cacheState[i] stays null, which marks this request as failed
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void printTable(RunResult without, RunResult with, int differentPages) {
        System.out.println();
        System.out.println("Times are measured in this program: from sending a request until its answer has arrived.");
        System.out.println();
        printRow("", "Without Redis", "With Redis");
        printRow("Requests", String.valueOf(without.succeeded()), String.valueOf(with.succeeded()));
        printRow("Average ms", number(without.averageMs()), number(with.averageMs()));
        printRow("Median ms", number(without.medianMs()), number(with.medianMs()));
        printRow("Slowest ms", number(without.slowestMs()), number(with.slowestMs()));
        printRow("Requests per second", number(without.requestsPerSecond()), number(with.requestsPerSecond()));
        printRow("Cache HIT", "-", String.valueOf(with.count("HIT")));
        printRow("Cache MISS", "-", String.valueOf(with.count("MISS")));
        if (with.count("DOWN") > 0) {
            printRow("Cache DOWN", "-", String.valueOf(with.count("DOWN")));
        }
        if (without.failed() > 0 || with.failed() > 0) {
            printRow("Failed requests", String.valueOf(without.failed()), String.valueOf(with.failed()));
        }
        System.out.println();

        if (with.count("MISS") > 0) {
            System.out.println("Note: " + differentPages + " different pages were visited. The first visit to each is a"
                    + " MISS; users who ask for the same new page at the same moment each get one too.");
        }
        if (with.count("DOWN") > 0) {
            System.out.println("WARNING: mini-redis was not reachable for " + with.count("DOWN") + " of "
                    + with.succeeded() + " requests, so they were not cached.");
        }
        if (with.count("HIT") > 0 && with.averageMs() > 0 && with.averageMs() < without.averageMs()) {
            System.out.printf(Locale.ROOT, "Average request: %.1f times faster with Redis.%n",
                    without.averageMs() / with.averageMs());
        } else {
            System.out.println("No speed-up measured in this run.");
        }
    }

    private static void printRow(String name, String without, String with) {
        System.out.printf("%-20s %15s %15s%n", name, without, with);
    }

    /** A number with two decimals, always with a dot (not a comma). */
    private static String number(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    /** What one run measured: one time and one X-Cache value per request. */
    private static class RunResult {
        final long[] nanos;          // how long each request took, seen from this program
        final String[] cacheState;   // OFF, HIT, MISS or DOWN; null if the request failed
        long totalNanos;             // from the first request sent to the last answer received

        RunResult(int requests) {
            nanos = new long[requests];
            cacheState = new String[requests];
        }

        int succeeded() {
            int n = 0;
            for (String state : cacheState) {
                if (state != null) {
                    n++;
                }
            }
            return n;
        }

        int failed() {
            return cacheState.length - succeeded();
        }

        int count(String wanted) {
            int n = 0;
            for (String state : cacheState) {
                if (wanted.equals(state)) {
                    n++;
                }
            }
            return n;
        }

        /** The times of the successful requests in milliseconds, smallest first. */
        double[] sortedMs() {
            double[] ms = new double[succeeded()];
            int next = 0;
            for (int i = 0; i < nanos.length; i++) {
                if (cacheState[i] != null) {
                    ms[next] = nanos[i] / 1_000_000.0;
                    next++;
                }
            }
            Arrays.sort(ms);
            return ms;
        }

        double averageMs() {
            double[] ms = sortedMs();
            if (ms.length == 0) {
                return 0;
            }
            double sum = 0;
            for (double value : ms) {
                sum += value;
            }
            return sum / ms.length;
        }

        /** The middle value: half of the requests were faster, half were slower. */
        double medianMs() {
            double[] ms = sortedMs();
            if (ms.length == 0) {
                return 0;
            }
            int middle = ms.length / 2;
            if (ms.length % 2 == 1) {
                return ms[middle];
            }
            return (ms[middle - 1] + ms[middle]) / 2;
        }

        double slowestMs() {
            double[] ms = sortedMs();
            return ms.length == 0 ? 0 : ms[ms.length - 1];
        }

        double requestsPerSecond() {
            return succeeded() / (totalNanos / 1_000_000_000.0);
        }
    }
}
