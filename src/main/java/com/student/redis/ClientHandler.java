package com.student.redis;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Talks to one connected client: reads a command line, runs it, writes the
 * one-line reply, and repeats until the client disconnects.
 */
public class ClientHandler implements Runnable {

    private final Socket socket;
    private final CommandRegistry registry;

    public ClientHandler(Socket socket, CommandRegistry registry) {
        this.socket = socket;
        this.registry = registry;
    }

    /**
     * Serves the client until it closes the connection.
     * Network errors are caught here so one bad client cannot crash the server.
     * This runs on one of the server's worker threads.
     */
    @Override
    public void run() {
        // try-with-resources closes the socket and streams even if something fails
        try (Socket s = socket;
             BufferedReader in = new BufferedReader(
                     new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(s.getOutputStream(), false, StandardCharsets.UTF_8)) {

            // readLine() returns null when the client has closed the connection
            String line = in.readLine();
            while (line != null) {
                out.print(registry.execute(line) + "\n");
                out.flush();
                line = in.readLine();
            }
        } catch (IOException e) {
            System.err.println("Client connection ended with an error: " + e.getMessage());
        }
    }
}
