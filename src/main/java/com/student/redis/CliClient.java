package com.student.redis;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * A tiny command-line client for the demo, like a mini redis-cli.
 * Type a command, see the one-line reply. Type "quit" to leave.
 *
 * Usage: java -cp target/mini-redis-1.0.jar com.student.redis.CliClient [host] [port]
 */
public class CliClient implements AutoCloseable {

    /** Never wait longer than this for the server (connecting or replying). */
    static final int TIMEOUT_MILLIS = 5000;

    private final Socket socket;
    private final BufferedReader in;
    private final PrintWriter out;

    /** Connects to the server. */
    public CliClient(String host, int port) throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), TIMEOUT_MILLIS);
        socket.setSoTimeout(TIMEOUT_MILLIS);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        out = new PrintWriter(socket.getOutputStream(), false, StandardCharsets.UTF_8);
    }

    /**
     * Sends one command line and returns the server's one-line reply.
     *
     * @throws IOException if the server closed the connection or did not answer within 5 s
     */
    public String send(String line) throws IOException {
        out.print(line + "\n");
        out.flush();
        String reply = in.readLine();
        if (reply == null) {
            throw new IOException("the server closed the connection");
        }
        return reply;
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }

    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : "localhost";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : Main.DEFAULT_PORT;

        BufferedReader keyboard = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        try (CliClient client = new CliClient(host, port)) {
            System.out.println("Connected to " + host + ":" + port + ". Type quit to leave.");
            while (true) {
                System.out.print(host + ":" + port + "> ");
                System.out.flush();
                String line = keyboard.readLine();
                if (line == null || line.trim().equalsIgnoreCase("quit") || line.trim().equalsIgnoreCase("exit")) {
                    break; // end of input, or the user wants to leave
                }
                if (line.isBlank()) {
                    continue;
                }
                System.out.println(client.send(line));
            }
        } catch (IOException e) {
            System.err.println("Error talking to " + host + ":" + port + ": " + e.getMessage());
            System.err.println("Is the server running? Start it with: java -Xmx128m -jar target/mini-redis-1.0.jar");
        }
    }
}
