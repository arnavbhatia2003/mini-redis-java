package com.student.demo;

import java.util.ArrayDeque;
import java.util.Locale;

/**
 * Remembers the most recent requests the website answered: when, which page,
 * what the cache did (HIT, MISS, OFF or DOWN) and how long the server took.
 *
 * The analytics page asks for "everything after request number N" once a
 * second and draws its charts from that. Only the newest 20,000 requests are
 * kept, so memory use stays small. The methods are synchronized because the
 * website's 8 threads all write to the same log.
 */
public class RequestLog {

    static final int CAPACITY = 20_000;
    static final int MAX_PER_ANSWER = 5_000;

    private final ArrayDeque<Entry> entries = new ArrayDeque<>();
    private final long startedMillis = System.currentTimeMillis();
    private long lastNumber = 0;

    /** Adds one answered request. The oldest one is dropped when the log is full. */
    public synchronized void add(String page, String cacheState, double serverMillis) {
        lastNumber++;
        entries.addLast(new Entry(lastNumber, System.currentTimeMillis(), page, cacheState, serverMillis));
        if (entries.size() > CAPACITY) {
            entries.removeFirst();
        }
    }

    /**
     * The requests with a number bigger than "after", oldest first, as JSON:
     * {"started":..,"now":..,"last":N,"requests":[[number,timeMillis,"page","HIT",2.41], ...]}
     * At most 5,000 are sent at once; the page simply asks again for the rest.
     */
    public synchronized String jsonAfter(long after) {
        StringBuilder json = new StringBuilder();
        json.append("{\"started\":").append(startedMillis)
            .append(",\"now\":").append(System.currentTimeMillis())
            .append(",\"last\":").append(lastNumber)
            .append(",\"requests\":[");
        int sent = 0;
        for (Entry e : entries) {
            if (e.number > after && sent < MAX_PER_ANSWER) {
                if (sent > 0) {
                    json.append(",");
                }
                json.append("[").append(e.number)
                    .append(",").append(e.timeMillis)
                    .append(",\"").append(ShopQueries.escape(e.page)).append("\"")
                    .append(",\"").append(e.cacheState).append("\"")
                    .append(",").append(String.format(Locale.ROOT, "%.2f", e.serverMillis))
                    .append("]");
                sent++;
            }
        }
        json.append("]}");
        return json.toString();
    }

    /** One answered request. */
    private static class Entry {
        final long number;
        final long timeMillis;
        final String page;
        final String cacheState;
        final double serverMillis;

        Entry(long number, long timeMillis, String page, String cacheState, double serverMillis) {
            this.number = number;
            this.timeMillis = timeMillis;
            this.page = page;
            this.cacheState = cacheState;
            this.serverMillis = serverMillis;
        }
    }
}
