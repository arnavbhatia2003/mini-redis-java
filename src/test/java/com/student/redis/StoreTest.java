package com.student.redis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for the in-memory Store. */
class StoreTest {

    private Store store;

    @BeforeEach
    void setUp() {
        store = new Store(); // every test starts with an empty store
    }

    @Test
    void setThenGetReturnsValue() {
        store.set("name", "alice");
        assertEquals("alice", store.get("name"));
    }

    @Test
    void setAgainOverwritesOldValue() {
        store.set("name", "alice");
        store.set("name", "bob");
        assertEquals("bob", store.get("name"));
    }

    @Test
    void getMissingKeyReturnsNull() {
        assertNull(store.get("missing"));
    }

    @Test
    void deleteExistingKeyReturnsTrueAndRemovesIt() {
        store.set("name", "alice");
        assertTrue(store.delete("name"));
        assertNull(store.get("name"));
        assertFalse(store.exists("name"));
    }

    @Test
    void deleteMissingKeyReturnsFalse() {
        assertFalse(store.delete("missing"));
    }

    @Test
    void existsIsTrueOnlyAfterSet() {
        assertFalse(store.exists("name"));
        store.set("name", "alice");
        assertTrue(store.exists("name"));
    }
}
