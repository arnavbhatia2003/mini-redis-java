package com.student.redis;

import java.time.Instant;
import java.util.ArrayDeque;

/**
 * What the store keeps under one key: either a string or a list, plus when it
 * expires.
 *
 * The fields of an Entry never change after it is created (they are final). To
 * change the expiry we build a new Entry and swap it in. A list's contents can
 * change, but only inside Store's synchronized list methods.
 */
public final class Entry {

    private final ValueType type;
    private final String value;              // used when type is STRING
    private final ArrayDeque<String> list;   // used when type is LIST
    private final Instant expiresAt;         // null means "never expires"

    /** A string entry. */
    public Entry(String value, Instant expiresAt) {
        this.type = ValueType.STRING;
        this.value = value;
        this.list = null;
        this.expiresAt = expiresAt;
    }

    /** A list entry. */
    public Entry(ArrayDeque<String> list, Instant expiresAt) {
        this.type = ValueType.LIST;
        this.value = null;
        this.list = list;
        this.expiresAt = expiresAt;
    }

    public ValueType getType() {
        return type;
    }

    public String getValue() {
        return value;
    }

    public ArrayDeque<String> getList() {
        return list;
    }

    /** When this entry expires, or null if it never does. */
    public Instant getExpiresAt() {
        return expiresAt;
    }

    /** True once the expiry time has been reached. */
    public boolean isExpired(Instant now) {
        return expiresAt != null && !now.isBefore(expiresAt);
    }

    /** A copy of this entry with a different expiry time (a list copy shares the same list). */
    public Entry withExpiry(Instant newExpiresAt) {
        if (type == ValueType.LIST) {
            return new Entry(list, newExpiresAt);
        }
        return new Entry(value, newExpiresAt);
    }
}
