package com.student.demo;

import com.student.redis.CliClient;
import com.student.redis.Replies;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

/**
 * A small cache helper that stores text in OUR mini-redis server.
 *
 * Every call opens a connection, sends one command and closes the connection
 * again. That matters because the server has only 8 worker threads and every
 * open connection keeps one of them busy.
 *
 * The server's protocol is one line of words separated by spaces, so a value
 * cannot contain a space. JSON does. We therefore turn each value into Base64
 * text (letters, digits, + / = only) before SET and turn it back after GET.
 */
public class RedisCache {

    private final String host;
    private final int port;

    public RedisCache(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /**
     * Returns the value stored under the key, or null if there is none
     * (never stored, deleted, or expired).
     */
    public String get(String key) throws CacheUnavailableException {
        checkKey(key);
        String reply = sendOne("GET " + key);
        if (reply.equals(Replies.NIL)) {
            return null;
        }
        checkNotError(reply);
        try {
            return decode(reply);
        } catch (IllegalArgumentException e) {
            // Not Base64, so this value was not stored by us. Treat it as "nothing cached".
            return null;
        }
    }

    /** Stores the value under the key. The server forgets it after the given seconds. */
    public void put(String key, String value, int seconds) throws CacheUnavailableException {
        checkKey(key);
        if (value.isEmpty()) {
            throw new IllegalArgumentException("cannot cache an empty value");
        }
        String reply = sendOne("SET " + key + " " + encode(value) + " EX " + seconds);
        if (!reply.equals(Replies.OK)) {
            throw new CacheUnavailableException("mini-redis did not accept SET: " + reply);
        }
    }

    /** Removes the key. Returns true if there was something to remove. */
    public boolean delete(String key) throws CacheUnavailableException {
        checkKey(key);
        String reply = sendOne("DEL " + key);
        checkNotError(reply);
        return reply.equals(Replies.integer(1));
    }

    /**
     * Removes many keys over ONE short connection (one DEL line per key), then closes it.
     * Returns how many of them really existed.
     */
    public int deleteAll(List<String> keys) throws CacheUnavailableException {
        for (String key : keys) {
            checkKey(key);
        }
        int removed = 0;
        try (CliClient client = new CliClient(host, port)) {
            for (String key : keys) {
                String reply = client.send("DEL " + key);
                checkNotError(reply);
                if (reply.equals(Replies.integer(1))) {
                    removed++;
                }
            }
        } catch (IOException e) {
            throw new CacheUnavailableException(
                    "mini-redis is not reachable at " + host + ":" + port + " (" + e.getMessage() + ")", e);
        }
        return removed;
    }

    /** Text to Base64, so it has no spaces and fits in one protocol word. */
    static String encode(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    /** Base64 back to the original text. */
    static String decode(String encoded) {
        return new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
    }

    /**
     * Opens a short-lived connection, sends one command and returns the reply line.
     * try-with-resources closes the connection even if something goes wrong.
     */
    private String sendOne(String line) throws CacheUnavailableException {
        try (CliClient client = new CliClient(host, port)) {
            return client.send(line);
        } catch (IOException e) {
            throw new CacheUnavailableException(
                    "mini-redis is not reachable at " + host + ":" + port + " (" + e.getMessage() + ")", e);
        }
    }

    /** Error replies always start with "ERR " or "WRONGTYPE ". Base64 never contains a space. */
    private static void checkNotError(String reply) throws CacheUnavailableException {
        if (reply.startsWith("ERR ") || reply.startsWith("WRONGTYPE ")) {
            throw new CacheUnavailableException("mini-redis replied with an error: " + reply);
        }
    }

    /** A key is one protocol word, so it must not be empty or contain spaces. */
    private static void checkKey(String key) {
        boolean oneWord = !key.isEmpty();
        for (int i = 0; i < key.length(); i++) {
            if (Character.isWhitespace(key.charAt(i))) {
                oneWord = false;
            }
        }
        if (!oneWord) {
            throw new IllegalArgumentException("a cache key must be one word without spaces: '" + key + "'");
        }
    }
}
