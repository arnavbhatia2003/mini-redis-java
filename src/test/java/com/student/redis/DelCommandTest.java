package com.student.redis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

/** Tests for DEL. */
class DelCommandTest {

    private final Store store = new Store();
    private final DelCommand del = new DelCommand(store);

    @Test
    void delExistingKeyRepliesOneAndRemovesIt() {
        store.set("name", "alice");
        assertEquals("(integer) 1", del.execute(new String[] {"DEL", "name"}));
        assertFalse(store.exists("name"));
    }

    @Test
    void delMissingKeyRepliesZero() {
        assertEquals("(integer) 0", del.execute(new String[] {"DEL", "missing"}));
    }

    @Test
    void delWithoutKeyIsAnError() {
        assertEquals("ERR wrong number of arguments for 'DEL'", del.execute(new String[] {"DEL"}));
    }
}
