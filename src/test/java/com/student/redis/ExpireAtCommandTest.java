package com.student.redis;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for EXPIREAT (used by the append-only file to save exact expiry times). */
class ExpireAtCommandTest {

    private TestClock clock;
    private CommandRegistry registry;
    private long nowSeconds;

    @BeforeEach
    void setUp() {
        clock = new TestClock();
        registry = new CommandRegistry(new Store(clock));
        nowSeconds = clock.instant().getEpochSecond();
    }

    @Test
    void keyExpiresAtTheGivenMoment() {
        registry.execute("SET name alice");
        assertEquals("(integer) 1", registry.execute("EXPIREAT name " + (nowSeconds + 30)));
        assertEquals("(integer) 30", registry.execute("TTL name"));
        clock.advanceSeconds(30);
        assertEquals("(nil)", registry.execute("GET name"));
    }

    @Test
    void momentInThePastExpiresTheKeyAtOnce() {
        registry.execute("SET name alice");
        assertEquals("(integer) 1", registry.execute("EXPIREAT name " + (nowSeconds - 5)));
        assertEquals("(integer) 0", registry.execute("EXISTS name"));
    }

    @Test
    void missingKeyAndBadInputs() {
        assertEquals("(integer) 0", registry.execute("EXPIREAT missing " + (nowSeconds + 30)));
        assertEquals("ERR value is not an integer or out of range", registry.execute("EXPIREAT name tomorrow"));
        assertEquals("ERR wrong number of arguments for 'EXPIREAT'", registry.execute("EXPIREAT name"));
    }
}
