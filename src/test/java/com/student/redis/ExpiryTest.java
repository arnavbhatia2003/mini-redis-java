package com.student.redis;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for SET ... EX, TTL and EXPIRE, using a fake clock so no test has to sleep. */
class ExpiryTest {

    private TestClock clock;
    private Store store;
    private CommandRegistry registry;

    @BeforeEach
    void setUp() {
        clock = new TestClock();
        store = new Store(clock);
        registry = new CommandRegistry(store);
    }

    @Test
    void keyWithExSurvivesUntilItsTimeThenDisappears() {
        assertEquals("OK", registry.execute("SET token abc EX 10"));
        clock.advanceSeconds(9);
        assertEquals("abc", registry.execute("GET token"));
        clock.advanceSeconds(1);
        assertEquals("(nil)", registry.execute("GET token"));
        assertEquals("(integer) 0", registry.execute("EXISTS token"));
    }

    @Test
    void expiredKeyIsRemovedFromTheMapWhenRead() {
        registry.execute("SET token abc EX 5");
        clock.advanceSeconds(6);
        assertEquals(1, store.size()); // lazy: still in the map until someone reads it
        assertEquals("(nil)", registry.execute("GET token"));
        assertEquals(0, store.size()); // the read removed it
    }

    @Test
    void ttlCountsDown() {
        registry.execute("SET token abc EX 10");
        assertEquals("(integer) 10", registry.execute("TTL token"));
        clock.advanceSeconds(3);
        assertEquals("(integer) 7", registry.execute("TTL token"));
        clock.advanceMillis(500);
        assertEquals("(integer) 7", registry.execute("TTL token")); // 6.5 s left rounds up to 7
    }

    @Test
    void ttlIsMinusOneWithoutExpiryAndMinusTwoWhenMissing() {
        registry.execute("SET name alice");
        assertEquals("(integer) -1", registry.execute("TTL name"));
        assertEquals("(integer) -2", registry.execute("TTL missing"));
        registry.execute("SET token abc EX 1");
        clock.advanceSeconds(1);
        assertEquals("(integer) -2", registry.execute("TTL token"));
    }

    @Test
    void expireAddsAnExpiryToAnExistingKey() {
        registry.execute("SET name alice");
        assertEquals("(integer) 1", registry.execute("EXPIRE name 5"));
        assertEquals("(integer) 5", registry.execute("TTL name"));
        clock.advanceSeconds(5);
        assertEquals("(nil)", registry.execute("GET name"));
    }

    @Test
    void expireOnMissingKeyRepliesZero() {
        assertEquals("(integer) 0", registry.execute("EXPIRE missing 5"));
    }

    @Test
    void plainSetRemovesOldExpiry() {
        registry.execute("SET token abc EX 5");
        registry.execute("SET token xyz");
        assertEquals("(integer) -1", registry.execute("TTL token"));
        clock.advanceSeconds(10);
        assertEquals("xyz", registry.execute("GET token"));
    }

    @Test
    void settingAgainWithLongerExpiryResetsIt() {
        registry.execute("SET token abc EX 5");
        clock.advanceSeconds(4);
        registry.execute("SET token abc EX 20");
        clock.advanceSeconds(4);
        assertEquals("abc", registry.execute("GET token"));
    }

    @Test
    void exOptionIsCaseInsensitive() {
        assertEquals("OK", registry.execute("SET token abc ex 5"));
        assertEquals("(integer) 5", registry.execute("TTL token"));
    }

    @Test
    void badExpiryValuesAreErrors() {
        assertEquals("ERR value is not an integer or out of range", registry.execute("SET k v EX soon"));
        assertEquals("ERR invalid expire time in 'SET'", registry.execute("SET k v EX 0"));
        assertEquals("ERR syntax error", registry.execute("SET k v EX"));
        assertEquals("ERR syntax error", registry.execute("SET k v PX 5"));
        assertEquals("ERR value is not an integer or out of range", registry.execute("EXPIRE k soon"));
        assertEquals("ERR wrong number of arguments for 'TTL'", registry.execute("TTL"));
        assertEquals("(integer) 0", registry.execute("EXISTS k")); // nothing was stored
    }

    @Test
    void deleteOfExpiredKeyRepliesZero() {
        registry.execute("SET token abc EX 1");
        clock.advanceSeconds(2);
        assertEquals("(integer) 0", registry.execute("DEL token"));
    }
}
