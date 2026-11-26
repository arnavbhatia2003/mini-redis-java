package com.student.redis;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Tests for GET. */
class GetCommandTest {

    private final Store store = new Store();
    private final GetCommand get = new GetCommand(store);

    @Test
    void getReturnsStoredValue() {
        store.set("name", "alice");
        assertEquals("alice", get.execute(new String[] {"GET", "name"}));
    }

    @Test
    void getMissingKeyRepliesNil() {
        assertEquals("(nil)", get.execute(new String[] {"GET", "missing"}));
    }

    @Test
    void getWithoutKeyIsAnError() {
        assertEquals("ERR wrong number of arguments for 'GET'", get.execute(new String[] {"GET"}));
    }
}
