package com.student.redis;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Tests for SET. */
class SetCommandTest {

    private final Store store = new Store();
    private final SetCommand set = new SetCommand(store);

    @Test
    void setStoresValueAndRepliesOk() {
        assertEquals("OK", set.execute(new String[] {"SET", "name", "alice"}));
        assertEquals("alice", store.get("name"));
    }

    @Test
    void setWithMissingValueIsAnError() {
        assertEquals("ERR wrong number of arguments for 'SET'", set.execute(new String[] {"SET", "name"}));
    }
}
