package com.student.redis;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Tests for EXISTS. */
class ExistsCommandTest {

    private final Store store = new Store();
    private final ExistsCommand exists = new ExistsCommand(store);

    @Test
    void existsRepliesOneForStoredKey() {
        store.set("name", "alice");
        assertEquals("(integer) 1", exists.execute(new String[] {"EXISTS", "name"}));
    }

    @Test
    void existsRepliesZeroForMissingKey() {
        assertEquals("(integer) 0", exists.execute(new String[] {"EXISTS", "missing"}));
    }

    @Test
    void existsWithTooManyWordsIsAnError() {
        assertEquals("ERR wrong number of arguments for 'EXISTS'",
                exists.execute(new String[] {"EXISTS", "a", "b"}));
    }
}
