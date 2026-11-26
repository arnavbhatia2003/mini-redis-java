package com.student.redis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Starts the real server on a free port and talks to it over TCP. */
class RedisServerTest {

    private RedisServer server;
    private Thread serverThread;

    @BeforeEach
    void startServer() throws IOException {
        server = new RedisServer(0, new CommandRegistry(new Store())); // port 0 = any free port
        serverThread = new Thread(server::start);
        serverThread.start();
    }

    @AfterEach
    void stopServer() throws InterruptedException {
        server.stop();
        serverThread.join(5000);
        assertFalse(serverThread.isAlive(), "server thread should have stopped");
    }

    @Test
    void clientCanSetAndGetOverTheNetwork() throws IOException {
        try (Socket socket = connect()) {
            BufferedReader in = reader(socket);
            PrintWriter out = writer(socket);

            out.print("SET name alice\n");
            out.flush();
            assertEquals("OK", in.readLine());

            out.print("GET name\n");
            out.flush();
            assertEquals("alice", in.readLine());

            out.print("nonsense\n");
            out.flush();
            assertEquals("ERR unknown command 'NONSENSE'", in.readLine());
        }
    }

    @Test
    void dataStaysAfterClientReconnects() throws IOException {
        try (Socket first = connect()) {
            PrintWriter out = writer(first);
            out.print("SET city pune\n");
            out.flush();
            assertEquals("OK", reader(first).readLine());
        }
        try (Socket second = connect()) {
            PrintWriter out = writer(second);
            out.print("GET city\n");
            out.flush();
            assertEquals("pune", reader(second).readLine());
        }
    }

    @Test
    void twoClientsConnectedAtOnceShareData() throws IOException {
        // A server that serves one client at a time would give the second client
        // no reply until the first left. Here both are served at the same time.
        try (Socket first = connect(); Socket second = connect()) {
            BufferedReader firstIn = reader(first);
            PrintWriter firstOut = writer(first);
            BufferedReader secondIn = reader(second);
            PrintWriter secondOut = writer(second);

            firstOut.print("SET a 1\n");
            firstOut.flush();
            assertEquals("OK", firstIn.readLine());

            secondOut.print("GET a\n");
            secondOut.flush();
            assertEquals("1", secondIn.readLine()); // first is still connected

            secondOut.print("SET a 2\n");
            secondOut.flush();
            assertEquals("OK", secondIn.readLine());

            firstOut.print("GET a\n");
            firstOut.flush();
            assertEquals("2", firstIn.readLine());
        }
    }

    @Test
    void stopDisconnectsClientsThatAreStillConnected() throws Exception {
        Socket client = connect();
        PrintWriter out = writer(client);
        out.print("SET a 1\n");
        out.flush();
        BufferedReader in = reader(client);
        assertEquals("OK", in.readLine());

        server.stop(); // the client never disconnected itself
        serverThread.join(5000);
        assertFalse(serverThread.isAlive());
        try {
            assertNull(in.readLine()); // the server closed our connection
        } catch (IOException e) {
            // a "connection reset" is also an acceptable way to be disconnected
        }
        client.close();
    }

    private Socket connect() throws IOException {
        Socket socket = new Socket("localhost", server.getPort());
        socket.setSoTimeout(5000); // never wait more than 5 s for a reply
        return socket;
    }

    private static BufferedReader reader(Socket socket) throws IOException {
        return new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
    }

    private static PrintWriter writer(Socket socket) throws IOException {
        return new PrintWriter(socket.getOutputStream(), false, StandardCharsets.UTF_8);
    }
}
