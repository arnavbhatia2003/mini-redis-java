package com.student.redis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Many threads writing to the same Store at once must not lose any keys.
 * Eight tasks each set 100 keys through our thread pool, then the keys are counted.
 */
class StoreConcurrencyTest {

    private final ExecutorService pool = Executors.newFixedThreadPool(8);

    @AfterEach
    void shutDownPool() throws InterruptedException {
        pool.shutdownNow();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
    }

    @Test
    void eightThreadsTimesHundredSetsGivesEightHundredKeys() throws Exception {
        Store store = new Store();
        List<Future<?>> results = new ArrayList<>();

        for (int t = 0; t < 8; t++) {
            final int threadNumber = t; // lambdas need an (effectively) final variable
            results.add(pool.submit(() -> {
                for (int i = 0; i < 100; i++) {
                    store.set("key-" + threadNumber + "-" + i, "value");
                }
            }));
        }
        for (Future<?> result : results) {
            result.get(5, TimeUnit.SECONDS); // wait for each task; fails the test if one threw
        }

        assertEquals(800, store.size());
    }
}
