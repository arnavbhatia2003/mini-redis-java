package com.student.redis;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The in-memory key-value store: the "database" of our mini Redis.
 *
 * The map is private, so the rest of the program can only change data through
 * the methods below (encapsulation). Many client threads use the store at the
 * same time, so it uses a ConcurrentHashMap.
 *
 * Expiry is "lazy": an expired key is only removed when someone tries to use it
 * (see getLive). There is no background thread scanning for old keys.
 *
 * A key holds either a string or a list (see ValueType). Using a string command
 * on a list, or the other way round, throws WrongTypeException.
 */
public class Store {

    private final Map<String, Entry> data = new ConcurrentHashMap<>();
    private final Clock clock;

    /** A store that uses the real system clock. */
    public Store() {
        this(Clock.systemUTC());
    }

    /** A store that reads the time from the given clock (tests pass a fake clock). */
    public Store(Clock clock) {
        this.clock = clock;
    }

    // Every method that CHANGES the map is synchronized (on this Store), the same
    // lock the list methods use. So two writes to the same key, e.g. LPOP and
    // EXPIRE, or LPUSH and SET, can never interleave half-way. Reads (get,
    // exists, ttl) don't need the lock: entries they see are never half-built.

    /** Saves the value under the key with no expiry, replacing any old value and expiry. */
    public synchronized void set(String key, String value) {
        data.put(key, new Entry(value, null));
    }

    /** Saves the value under the key; it expires after the given number of seconds. */
    public synchronized void set(String key, String value, long seconds) {
        data.put(key, new Entry(value, now().plusSeconds(seconds)));
    }

    /**
     * Returns the value for the key, or null if the key does not exist or has expired.
     *
     * @throws WrongTypeException if the key holds a list
     */
    public String get(String key) {
        Entry entry = getLive(key);
        if (entry == null) {
            return null;
        }
        if (entry.getType() != ValueType.STRING) {
            throw new WrongTypeException();
        }
        return entry.getValue();
    }

    // ----- lists -----
    // ArrayDeque is not thread-safe, so every method that touches a list is
    // synchronized too: only one thread at a time can be inside any of them.

    /** Adds the values to the front of the list, one by one. Creates the list if needed. Returns the new length. */
    public synchronized int lpush(String key, List<String> values) {
        ArrayDeque<String> list = listForWriting(key);
        for (String value : values) {
            list.addFirst(value);
        }
        return list.size();
    }

    /** Adds the values to the end of the list. Creates the list if needed. Returns the new length. */
    public synchronized int rpush(String key, List<String> values) {
        ArrayDeque<String> list = listForWriting(key);
        for (String value : values) {
            list.addLast(value);
        }
        return list.size();
    }

    /** Removes and returns the first element, or null if there is no list. An emptied list is deleted. */
    public synchronized String lpop(String key) {
        Entry entry = getLive(key);
        if (entry == null) {
            return null;
        }
        if (entry.getType() != ValueType.LIST) {
            throw new WrongTypeException();
        }
        String first = entry.getList().pollFirst();
        if (entry.getList().isEmpty()) {
            data.remove(key, entry); // Redis never keeps empty lists
        }
        return first;
    }

    /**
     * Elements from index start to stop, both included. Negative indexes count
     * from the end (-1 is the last element). Out-of-range indexes are trimmed.
     */
    public synchronized List<String> lrange(String key, long start, long stop) {
        List<String> result = new ArrayList<>();
        Entry entry = getLive(key);
        if (entry == null) {
            return result;
        }
        if (entry.getType() != ValueType.LIST) {
            throw new WrongTypeException();
        }
        ArrayDeque<String> list = entry.getList();
        int size = list.size();
        if (start < 0) {
            start = start + size; // e.g. -2 with 5 elements becomes 3
        }
        if (stop < 0) {
            stop = stop + size;
        }
        if (start < 0) {
            start = 0;
        }
        if (stop >= size) {
            stop = size - 1;
        }
        // ArrayDeque has no get(i), so walk it and keep a position counter
        int index = 0;
        for (String element : list) {
            if (index >= start && index <= stop) {
                result.add(element);
            }
            index++;
        }
        return result;
    }

    /** The list stored under the key, creating an empty one if the key is missing. */
    private ArrayDeque<String> listForWriting(String key) {
        Entry entry = getLive(key);
        if (entry == null) {
            entry = new Entry(new ArrayDeque<String>(), null);
            data.put(key, entry);
        }
        if (entry.getType() != ValueType.LIST) {
            throw new WrongTypeException();
        }
        return entry.getList();
    }

    /** Removes the key. Returns true if it existed, false if there was nothing to remove. */
    public synchronized boolean delete(String key) {
        return getLive(key) != null && data.remove(key) != null;
    }

    /** Returns true if the key exists and has not expired. */
    public boolean exists(String key) {
        return getLive(key) != null;
    }

    /**
     * Seconds until the key expires (rounded up), -1 if it never expires,
     * or -2 if the key does not exist. These are the numbers real Redis uses.
     */
    public long ttl(String key) {
        Entry entry = getLive(key);
        if (entry == null) {
            return -2;
        }
        if (entry.getExpiresAt() == null) {
            return -1;
        }
        long millisLeft = Duration.between(now(), entry.getExpiresAt()).toMillis();
        return (millisLeft + 999) / 1000; // round up, so a fresh "EX 10" shows 10, not 9
    }

    /** Makes the key expire after the given number of seconds. Returns false if the key does not exist. */
    public boolean expire(String key, long seconds) {
        return expireAt(key, now().plusSeconds(seconds));
    }

    /** Makes the key expire at an exact moment. Returns false if the key does not exist. */
    public synchronized boolean expireAt(String key, Instant when) {
        Entry entry = getLive(key);
        if (entry == null) {
            return false;
        }
        // replace() only succeeds if nobody changed the key in the meantime
        return data.replace(key, entry, entry.withExpiry(when));
    }

    /** When the key expires, or null if it has no expiry or does not exist. */
    public Instant getExpiresAt(String key) {
        Entry entry = getLive(key);
        return entry == null ? null : entry.getExpiresAt();
    }

    /** How many keys are stored, including expired ones not yet removed (used by tests). */
    public int size() {
        return data.size();
    }

    /**
     * Lazy expiry: returns the entry for the key, or null if it is missing.
     * If the entry has expired, it is removed now and null is returned.
     */
    private Entry getLive(String key) {
        Entry entry = data.get(key);
        if (entry != null && entry.isExpired(now())) {
            data.remove(key, entry); // only removes this exact old entry, not a newer one
            return null;
        }
        return entry;
    }

    private Instant now() {
        return clock.instant();
    }
}
