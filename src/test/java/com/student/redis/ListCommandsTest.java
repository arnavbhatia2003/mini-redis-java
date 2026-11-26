package com.student.redis;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for LPUSH, RPUSH, LPOP and LRANGE. */
class ListCommandsTest {

    private static final String WRONGTYPE = "WRONGTYPE Operation against a key holding the wrong kind of value";

    private CommandRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new CommandRegistry(new Store());
    }

    @Test
    void rpushAddsToTheEndAndRepliesWithLength() {
        assertEquals("(integer) 1", registry.execute("RPUSH fruits apple"));
        assertEquals("(integer) 3", registry.execute("RPUSH fruits banana cherry"));
        assertEquals("apple banana cherry", registry.execute("LRANGE fruits 0 -1"));
    }

    @Test
    void lpushAddsEachValueToTheFront() {
        assertEquals("(integer) 3", registry.execute("LPUSH letters a b c"));
        assertEquals("c b a", registry.execute("LRANGE letters 0 -1"));
    }

    @Test
    void lrangeWithPositiveIndexesIncludesStop() {
        registry.execute("RPUSH l a b c d e");
        assertEquals("a b", registry.execute("LRANGE l 0 1"));
        assertEquals("c d e", registry.execute("LRANGE l 2 4"));
    }

    @Test
    void lrangeWithNegativeIndexesCountsFromTheEnd() {
        registry.execute("RPUSH l a b c d e");
        assertEquals("d e", registry.execute("LRANGE l -2 -1"));
        assertEquals("a b c", registry.execute("LRANGE l 0 -3"));
        assertEquals("a b c d e", registry.execute("LRANGE l -100 100"));
    }

    @Test
    void lrangeOutOfRangeGivesEmptyList() {
        registry.execute("RPUSH l a b c");
        assertEquals("(empty list)", registry.execute("LRANGE l 5 10"));
        assertEquals("(empty list)", registry.execute("LRANGE l 2 1"));
        assertEquals("(empty list)", registry.execute("LRANGE missing 0 -1"));
    }

    @Test
    void lpopRemovesFromTheFrontUntilEmptyThenKeyIsGone() {
        registry.execute("RPUSH l a b");
        assertEquals("a", registry.execute("LPOP l"));
        assertEquals("b", registry.execute("LPOP l"));
        assertEquals("(nil)", registry.execute("LPOP l"));
        assertEquals("(integer) 0", registry.execute("EXISTS l"));
    }

    @Test
    void wrongTypeErrorsBothWays() {
        registry.execute("SET name alice");
        assertEquals(WRONGTYPE, registry.execute("LPUSH name x"));
        assertEquals(WRONGTYPE, registry.execute("RPUSH name x"));
        assertEquals(WRONGTYPE, registry.execute("LPOP name"));
        assertEquals(WRONGTYPE, registry.execute("LRANGE name 0 -1"));
        assertEquals("alice", registry.execute("GET name")); // nothing changed

        registry.execute("RPUSH l a");
        assertEquals(WRONGTYPE, registry.execute("GET l"));
    }

    @Test
    void setReplacesAListAndDelRemovesIt() {
        registry.execute("RPUSH l a b");
        assertEquals("OK", registry.execute("SET l text"));
        assertEquals("text", registry.execute("GET l"));

        registry.execute("RPUSH other a");
        assertEquals("(integer) 1", registry.execute("DEL other"));
        assertEquals("(empty list)", registry.execute("LRANGE other 0 -1"));
    }

    @Test
    void badArgumentsAreErrors() {
        assertEquals("ERR wrong number of arguments for 'LPUSH'", registry.execute("LPUSH l"));
        assertEquals("ERR wrong number of arguments for 'RPUSH'", registry.execute("RPUSH"));
        assertEquals("ERR wrong number of arguments for 'LPOP'", registry.execute("LPOP"));
        assertEquals("ERR wrong number of arguments for 'LRANGE'", registry.execute("LRANGE l 0"));
        assertEquals("ERR value is not an integer or out of range", registry.execute("LRANGE l zero 1"));
    }

    @Test
    void listsCanExpire() {
        TestClock clock = new TestClock();
        CommandRegistry timed = new CommandRegistry(new Store(clock));
        timed.execute("RPUSH l a b");
        assertEquals("(integer) 1", timed.execute("EXPIRE l 5"));
        assertEquals("a b", timed.execute("LRANGE l 0 -1"));
        clock.advanceSeconds(5);
        assertEquals("(empty list)", timed.execute("LRANGE l 0 -1"));
    }
}
