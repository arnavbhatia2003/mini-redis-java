package com.student.redis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

/**
 * Two threads hitting the same key at the worst possible moment.
 *
 * Races normally depend on luck. To make them happen every time, PausingClock
 * freezes one chosen thread the moment it asks for the time inside Store.getLive(),
 * which is just after it has read the entry from the map. While it is frozen,
 * the test runs a second command on another thread, then lets the first go on.
 */
class StoreRaceTest {

    /** Waits for the frozen thread / the second thread, but never longer than this. */
    private static final long WAIT_MILLIS = 5000;
    /** How long we give the second command to finish while the first is frozen. */
    private static final long SECOND_COMMAND_MILLIS = 300;

    @Test
    void lpopAndExpireAtTheSameMomentMustNotLeaveAnEmptyList() throws Exception {
        PausingClock clock = new PausingClock();
        Store store = new Store(clock);
        store.rpush("queue", List.of("only"));

        Thread popper = new Thread(() -> store.lpop("queue"), "popper");
        clock.pauseNextCallFrom(popper);
        popper.start();
        assertTrue(clock.awaitPaused(), "popper should reach getLive()");

        // popper has read the entry and is frozen; now EXPIRE runs on another thread
        Thread expirer = new Thread(() -> store.expire("queue", 100), "expirer");
        expirer.start();
        expirer.join(SECOND_COMMAND_MILLIS); // with the fix it waits for popper instead

        clock.resume();
        popper.join(WAIT_MILLIS);
        expirer.join(WAIT_MILLIS);
        assertFalse(popper.isAlive());
        assertFalse(expirer.isAlive());

        // the only element was popped, so the key must be gone (Redis never keeps empty lists)
        assertFalse(store.exists("queue"), "an empty list was left behind");
        assertEquals(List.of(), store.lrange("queue", 0, -1));
    }

    @Test
    void setDuringLpushMustNotLetThePushReportSuccessOnLostData() throws Exception {
        PausingClock clock = new PausingClock();
        Store store = new Store(clock);
        store.rpush("key", List.of("a"));

        AtomicReference<Integer> pushResult = new AtomicReference<>();
        Thread pusher = new Thread(() -> {
            try {
                pushResult.set(store.lpush("key", List.of("b")));
            } catch (WrongTypeException e) {
                pushResult.set(-1); // fine: SET won, so the key holds a string
            }
        }, "pusher");
        clock.pauseNextCallFrom(pusher);
        pusher.start();
        assertTrue(clock.awaitPaused(), "pusher should reach getLive()");

        AtomicBoolean setFinished = new AtomicBoolean(false);
        Thread setter = new Thread(() -> {
            store.set("key", "text");
            setFinished.set(true);
        }, "setter");
        setter.start();
        setter.join(SECOND_COMMAND_MILLIS);
        boolean setFinishedWhilePushWasInProgress = setFinished.get();

        clock.resume();
        pusher.join(WAIT_MILLIS);
        setter.join(WAIT_MILLIS);
        assertFalse(pusher.isAlive());
        assertFalse(setter.isAlive());

        // If SET finished first, the push must have failed (WRONGTYPE).
        // Reporting "(integer) 2" for a list that SET already replaced would be a lie.
        boolean pushReportedSuccess = pushResult.get() != null && pushResult.get() > 0;
        assertFalse(setFinishedWhilePushWasInProgress && pushReportedSuccess,
                "LPUSH said it pushed, but SET had already replaced the list");
        assertEquals("text", store.get("key"));
    }

    /**
     * A clock that can freeze one particular thread the next time it asks for the
     * time. All other threads (and later calls) get the time straight away.
     */
    private static final class PausingClock extends Clock {

        private final Instant now = Instant.parse("2026-01-01T00:00:00Z");
        private final CountDownLatch paused = new CountDownLatch(1);
        private final CountDownLatch resumed = new CountDownLatch(1);
        private volatile Thread target;

        void pauseNextCallFrom(Thread thread) {
            target = thread;
        }

        boolean awaitPaused() throws InterruptedException {
            return paused.await(WAIT_MILLIS, TimeUnit.MILLISECONDS);
        }

        void resume() {
            resumed.countDown();
        }

        @Override
        public Instant instant() {
            if (Thread.currentThread() == target) {
                target = null; // only freeze once
                paused.countDown();
                try {
                    resumed.await(WAIT_MILLIS, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
