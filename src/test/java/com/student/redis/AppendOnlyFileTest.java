package com.student.redis;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for persistence. Each test gets its own empty temporary folder (@TempDir),
 * so the real data.aof is never touched.
 */
class AppendOnlyFileTest {

    @TempDir
    Path tempDir;

    private Path file;
    private TestClock clock;

    @BeforeEach
    void setUp() {
        file = tempDir.resolve("test.aof");
        clock = new TestClock();
    }

    /** A fresh "server": new empty store, replays the file, then saves new writes to it. */
    private CommandRegistry startServer() throws IOException {
        CommandRegistry registry = new CommandRegistry(new Store(clock));
        AppendOnlyFile aof = new AppendOnlyFile(file);
        aof.replay(registry);
        registry.setAppendOnlyFile(aof);
        return registry;
    }

    @Test
    void dataSurvivesARestart() throws IOException {
        CommandRegistry first = startServer();
        first.execute("SET name alice");
        first.execute("SET city pune");
        first.execute("DEL city");

        CommandRegistry second = startServer(); // like restarting the program
        assertEquals("alice", second.execute("GET name"));
        assertEquals("(nil)", second.execute("GET city"));
    }

    @Test
    void onlySuccessfulWritesAreSaved() throws IOException {
        CommandRegistry registry = startServer();
        registry.execute("SET name alice");
        registry.execute("GET name");          // read: not saved
        registry.execute("EXISTS name");       // read: not saved
        registry.execute("SET broken");        // error: not saved
        registry.execute("unknown stuff");     // error: not saved

        assertEquals(List.of("SET name alice"), Files.readAllLines(file, StandardCharsets.UTF_8));
    }

    @Test
    void expiryIsSavedAsAnExactTime() throws IOException {
        CommandRegistry registry = startServer();
        registry.execute("SET token abc EX 10");
        long expectedTime = clock.instant().plusSeconds(10).getEpochSecond();

        assertEquals(List.of("SET token abc", "EXPIREAT token " + expectedTime),
                Files.readAllLines(file, StandardCharsets.UTF_8));
    }

    @Test
    void expiryKeepsCountingDownAcrossARestart() throws IOException {
        startServer().execute("SET token abc EX 10");
        clock.advanceSeconds(4); // the server was down for 4 seconds

        CommandRegistry restarted = startServer();
        assertEquals("(integer) 6", restarted.execute("TTL token")); // not a fresh 10
        clock.advanceSeconds(6);
        assertEquals("(nil)", restarted.execute("GET token"));
    }

    @Test
    void keyThatExpiredWhileServerWasDownStaysExpired() throws IOException {
        CommandRegistry registry = startServer();
        registry.execute("SET token abc EX 5");
        registry.execute("SET name alice");
        clock.advanceSeconds(60);

        CommandRegistry restarted = startServer();
        assertEquals("(nil)", restarted.execute("GET token"));
        assertEquals("alice", restarted.execute("GET name"));
    }

    @Test
    void expireCommandIsAlsoSavedAsExactTime() throws IOException {
        CommandRegistry registry = startServer();
        registry.execute("SET name alice");
        registry.execute("EXPIRE name 30");
        clock.advanceSeconds(10);

        assertEquals("(integer) 20", startServer().execute("TTL name"));
    }

    @Test
    void listsSurviveARestart() throws IOException {
        CommandRegistry first = startServer();
        first.execute("RPUSH queue a b c");
        first.execute("LPUSH queue z");
        first.execute("LPOP queue");
        first.execute("LPUSH name x"); // fine: name is a new list key
        first.execute("SET word hi");
        first.execute("LPUSH word x"); // WRONGTYPE error: not saved

        CommandRegistry restarted = startServer();
        assertEquals("a b c", restarted.execute("LRANGE queue 0 -1"));
        assertEquals("hi", restarted.execute("GET word"));
    }

    @Test
    void replayDoesNotSaveTheLinesASecondTime() throws IOException {
        startServer().execute("SET name alice");
        startServer();
        startServer();
        assertEquals(1, Files.readAllLines(file, StandardCharsets.UTF_8).size());
    }

    @Test
    void missingFileMeansEmptyStoreAndBadLinesAreSkipped() throws IOException {
        CommandRegistry registry = new CommandRegistry(new Store(clock));
        assertEquals(0, new AppendOnlyFile(file).replay(registry));

        Files.writeString(file, "SET a 1\nthis is not a command\n\nSET b 2\n", StandardCharsets.UTF_8);
        assertEquals(2, new AppendOnlyFile(file).replay(registry));
        assertEquals("1", registry.execute("GET a"));
        assertEquals("2", registry.execute("GET b"));
    }
}
