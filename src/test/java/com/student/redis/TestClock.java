package com.student.redis;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * A fake clock for tests. Time only moves when the test calls advanceSeconds(),
 * so expiry can be tested instantly, without Thread.sleep.
 */
class TestClock extends Clock {

    private Instant now = Instant.parse("2026-01-01T00:00:00Z");

    void advanceSeconds(long seconds) {
        now = now.plusSeconds(seconds);
    }

    void advanceMillis(long millis) {
        now = now.plusMillis(millis);
    }

    @Override
    public Instant instant() {
        return now;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this; // the zone doesn't matter for our tests
    }
}
