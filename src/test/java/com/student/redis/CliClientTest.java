package com.student.redis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** End-to-end tests: the real server (with persistence) driven by the real CLI client. */
class CliClientTest {

    @TempDir
    Path tempDir;

    private RedisServer server;
    private Thread serverThread;

    @BeforeEach
    void startServer() throws IOException {
        CommandRegistry registry = new CommandRegistry(new Store());
        registry.setAppendOnlyFile(new AppendOnlyFile(tempDir.resolve("test.aof")));
        server = new RedisServer(0, registry);
        serverThread = new Thread(server::start);
        serverThread.start();
    }

    @AfterEach
    void stopServer() throws InterruptedException {
        server.stop();
        serverThread.join(5000);
        assertFalse(serverThread.isAlive());
    }

    @Test
    void everyKindOfCommandWorksThroughTheClient() throws IOException {
        try (CliClient client = new CliClient("localhost", server.getPort())) {
            assertEquals("OK", client.send("SET name alice"));
            assertEquals("alice", client.send("GET name"));
            assertEquals("(integer) 1", client.send("EXISTS name"));
            assertEquals("OK", client.send("SET token abc EX 100"));
            assertEquals("(integer) 100", client.send("TTL token"));
            assertEquals("(integer) 1", client.send("EXPIRE name 50"));
            assertEquals("(integer) 3", client.send("RPUSH l a b c"));
            assertEquals("(integer) 4", client.send("LPUSH l z"));
            assertEquals("z", client.send("LPOP l"));
            assertEquals("a b c", client.send("LRANGE l 0 -1"));
            assertEquals("(integer) 1", client.send("DEL name"));
            assertEquals("ERR unknown command 'HELLO'", client.send("hello"));
        }
    }

    @Test
    void twoClientsShareTheSameData() throws IOException {
        try (CliClient alice = new CliClient("localhost", server.getPort());
             CliClient bob = new CliClient("localhost", server.getPort())) {
            assertEquals("OK", alice.send("SET greeting hello"));
            assertEquals("hello", bob.send("GET greeting"));
            assertEquals("(integer) 1", bob.send("RPUSH chat hi"));
            assertEquals("(integer) 2", alice.send("RPUSH chat hey"));
            assertEquals("hi hey", alice.send("LRANGE chat 0 -1"));
        }
    }

    @Test
    void clientReportsAnErrorWhenTheServerGoesAway() throws IOException {
        try (CliClient client = new CliClient("localhost", server.getPort())) {
            assertEquals("OK", client.send("SET a 1"));
            server.stop();
            assertThrows(IOException.class, () -> client.send("GET a"));
        }
    }
}
