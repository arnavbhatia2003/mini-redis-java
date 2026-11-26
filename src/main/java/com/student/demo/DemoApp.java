package com.student.demo;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

/**
 * The demo shop's web server. It shows what a cache is for.
 *
 * Every page of the shop needs numbers that are slow to work out (all 400,000
 * orders are read). Without the cache that happens on every request. With the
 * cache, the first request does the slow work and stores the answer in OUR
 * mini-redis server for 30 seconds; the following requests just fetch it.
 *
 * Usage: java -cp target/mini-redis-1.0.jar com.student.demo.DemoApp
 *            [--port 8080] [--redis-host localhost] [--redis-port 6379]
 *
 * Pages:      /  /products  /product?id=7  /analytics
 * Data:       /api/home  /api/products?category=backpack  /api/product?id=7
 *             (the cache is used unless the address also says cache=off)
 * Tools:      /api/clear-cache  /api/stats?after=0
 */
public class DemoApp {

    static final int CACHE_SECONDS = 30;
    static final int HTTP_THREADS = 8;
    static final String JSON = "application/json; charset=utf-8";

    private final ShopQueries queries;
    private final RedisCache cache;
    private final RequestLog requestLog = new RequestLog();

    public DemoApp(ShopQueries queries, RedisCache cache) {
        this.queries = queries;
        this.cache = cache;
    }

    public static void main(String[] args) throws IOException {
        int port = 8080;
        String redisHost = "localhost";
        int redisPort = 6379;
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals("--port")) {
                port = Integer.parseInt(args[i + 1]);
                i++; // skip the value we just read
            } else if (args[i].equals("--redis-host")) {
                redisHost = args[i + 1];
                i++;
            } else if (args[i].equals("--redis-port")) {
                redisPort = Integer.parseInt(args[i + 1]);
                i++;
            }
        }

        SalesData salesData = new SalesData(Path.of(SalesData.DEFAULT_FILE));
        System.out.println("Checking " + SalesData.DEFAULT_FILE + " (it is created on the first start) ...");
        if (salesData.createFileIfMissing()) {
            System.out.println("Created " + SalesData.DEFAULT_FILE + " with " + SalesData.ORDER_COUNT + " orders");
        }

        DemoApp app = new DemoApp(new ShopQueries(salesData), new RedisCache(redisHost, redisPort));

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        // a fixed pool: at most 8 requests are worked on at once, the rest wait their turn
        server.setExecutor(Executors.newFixedThreadPool(HTTP_THREADS));
        server.createContext("/", exchange -> app.handleFile(exchange));
        server.createContext("/api/home", exchange -> app.handleHome(exchange));
        server.createContext("/api/products", exchange -> app.handleProducts(exchange));
        server.createContext("/api/product", exchange -> app.handleProduct(exchange));
        server.createContext("/api/clear-cache", exchange -> app.handleClearCache(exchange));
        server.createContext("/api/stats", exchange -> app.handleStats(exchange));
        server.start();

        System.out.println("Shop:      http://localhost:" + port + "/");
        System.out.println("Analytics: http://localhost:" + port + "/analytics");
        System.out.println("Cache: mini-redis at " + redisHost + ":" + redisPort + " (Ctrl+C to stop)");
    }

    /** Sends one of the website's fixed files (pages, styles, scripts), or "404 not found". */
    void handleFile(HttpExchange exchange) throws IOException {
        long start = System.nanoTime();
        String fileName = StaticFiles.fileFor(exchange.getRequestURI().getPath());
        if (fileName == null) {
            sendText(exchange, 404, "text/plain; charset=utf-8", "Not found", null, millisSince(start));
            return;
        }
        byte[] bytes = StaticFiles.read(fileName);
        send(exchange, 200, StaticFiles.contentType(fileName), bytes, null, millisSince(start));
    }

    /** GET /api/home : the numbers for the home page. */
    void handleHome(HttpExchange exchange) throws IOException {
        answer(exchange, "/api/home", "shop:home", () -> queries.home());
    }

    /** GET /api/products?category=backpack : a product list. Without a category, all products. */
    void handleProducts(HttpExchange exchange) throws IOException {
        String category = queryParam(exchange.getRequestURI().getQuery(), "category");
        if (category == null) {
            category = ShopQueries.ALL;
        }
        if (!ShopQueries.isKnownCategory(category)) {
            sendText(exchange, 404, JSON, "{\"error\":\"there is no such category\"}", null, 0);
            return;
        }
        String wanted = category; // a lambda may only use variables that never change
        answer(exchange, "/api/products?category=" + category, "shop:products:" + category,
                () -> queries.products(wanted));
    }

    /** GET /api/product?id=7 : one product's page. */
    void handleProduct(HttpExchange exchange) throws IOException {
        int id = 0;
        try {
            id = Integer.parseInt(String.valueOf(queryParam(exchange.getRequestURI().getQuery(), "id")));
        } catch (NumberFormatException e) {
            // id stays 0, which is refused just below
        }
        if (id < 1 || id > SalesData.productNames().length) {
            sendText(exchange, 404, JSON, "{\"error\":\"there is no such product\"}", null, 0);
            return;
        }
        int wanted = id;
        answer(exchange, "/api/product?id=" + id, "shop:product:" + id, () -> queries.product(wanted));
    }

    /**
     * Answers one data request, with or without the cache, and notes it in the request log.
     * The X-Cache header says what happened:
     *   OFF  = the address said cache=off; the slow work was done
     *   HIT  = the answer came from mini-redis; no slow work
     *   MISS = mini-redis had no answer; slow work done and the answer stored for 30 s
     *   DOWN = mini-redis could not be reached; slow work done so the user still gets an answer
     */
    private void answer(HttpExchange exchange, String page, String cacheKey, SlowWork slowWork)
            throws IOException {
        long start = System.nanoTime();
        boolean useCache = !"off".equals(queryParam(exchange.getRequestURI().getQuery(), "cache"));

        String cacheState;
        String json;
        try {
            if (!useCache) {
                cacheState = "OFF";
                json = slowWork.run();
            } else {
                String cached = null;
                boolean cacheWorks = true;
                try {
                    cached = cache.get(cacheKey);
                } catch (CacheUnavailableException e) {
                    cacheWorks = false;
                    System.err.println("Cache not used: " + e.getMessage());
                }

                if (cached != null) {
                    cacheState = "HIT";
                    json = cached;
                } else if (cacheWorks) {
                    json = slowWork.run();
                    cacheState = json == null ? "MISS" : storeInCache(cacheKey, json);
                } else {
                    json = slowWork.run();
                    cacheState = "DOWN";
                }
            }
        } catch (IOException e) {
            // only the slow work (reading the sales file) can throw this here
            System.err.println("Could not read the sales file: " + e.getMessage());
            sendText(exchange, 500, JSON, "{\"error\":\"could not read the sales file\"}", null, millisSince(start));
            return;
        }

        if (json == null) {
            sendText(exchange, 404, JSON, "{\"error\":\"nothing was sold under this address\"}", null, millisSince(start));
            return;
        }
        double millis = millisSince(start);
        requestLog.add(page, cacheState, millis);
        sendText(exchange, 200, JSON, json, cacheState, millis);
    }

    /** Stores a fresh answer for 30 seconds. Returns MISS, or DOWN if mini-redis went away meanwhile. */
    private String storeInCache(String cacheKey, String json) {
        try {
            cache.put(cacheKey, json, CACHE_SECONDS);
            return "MISS";
        } catch (CacheUnavailableException e) {
            System.err.println("Answer not cached: " + e.getMessage());
            return "DOWN";
        }
    }

    /** GET /api/clear-cache : deletes every cached page, so the next cached request of each is a MISS. */
    void handleClearCache(HttpExchange exchange) throws IOException {
        long start = System.nanoTime();
        try {
            int removed = cache.deleteAll(allCacheKeys());
            String body = "{\"cleared\":true,\"removed\":" + removed + "}";
            sendText(exchange, 200, JSON, body, null, millisSince(start));
        } catch (CacheUnavailableException e) {
            System.err.println("Cache not cleared: " + e.getMessage());
            String body = "{\"cleared\":false,\"error\":\"mini-redis is not running\"}";
            sendText(exchange, 503, JSON, body, "DOWN", millisSince(start));
        }
    }

    /** Every key this website can ever store: the home page, 11 product lists and 200 product pages. */
    static List<String> allCacheKeys() {
        List<String> keys = new ArrayList<>();
        keys.add("shop:home");
        keys.add("shop:products:" + ShopQueries.ALL);
        for (String category : SalesData.categoryNames()) {
            keys.add("shop:products:" + ProductStats.slug(category));
        }
        for (int id = 1; id <= SalesData.productNames().length; id++) {
            keys.add("shop:product:" + id);
        }
        return keys;
    }

    /** GET /api/stats?after=120 : the requests answered after request number 120, for the analytics page. */
    void handleStats(HttpExchange exchange) throws IOException {
        long after = 0;
        try {
            after = Long.parseLong(String.valueOf(queryParam(exchange.getRequestURI().getQuery(), "after")));
        } catch (NumberFormatException e) {
            // no number given: send everything we still have
        }
        sendText(exchange, 200, JSON, requestLog.jsonAfter(after), null, 0);
    }

    /**
     * Finds one value in the part of an address after "?".
     * queryParam("id=7&cache=off", "cache") gives "off". Returns null if the name is not there.
     */
    static String queryParam(String query, String name) {
        if (query == null) {
            return null;
        }
        for (String part : query.split("&")) {
            if (part.startsWith(name + "=")) {
                return part.substring(name.length() + 1);
            }
        }
        return null;
    }

    private static double millisSince(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000.0;
    }

    private void sendText(HttpExchange exchange, int status, String contentType, String body,
                          String cacheState, double millis) throws IOException {
        send(exchange, status, contentType, body.getBytes(StandardCharsets.UTF_8), cacheState, millis);
    }

    /**
     * Sends one response and closes the exchange. Adds the X-Time-Ms header (how long
     * this request took on the server, in milliseconds) and, if given, the X-Cache header.
     */
    private void send(HttpExchange exchange, int status, String contentType, byte[] bytes,
                      String cacheState, double millis) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        // tell the browser never to keep its own copy, so every click really reaches this server
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        if (cacheState != null) {
            exchange.getResponseHeaders().set("X-Cache", cacheState);
        }
        exchange.getResponseHeaders().set("X-Time-Ms", String.format(Locale.ROOT, "%.2f", millis));

        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
        exchange.close();
    }

    /** The slow work of one request, handed to answer() so it can decide whether to run it. */
    interface SlowWork {
        String run() throws IOException;
    }
}
