package com.student.redis;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * The TCP server. It listens on a port and gives every connected client its own
 * ClientHandler, run by a fixed pool of worker threads, so many clients can be
 * served at the same time.
 */
public class RedisServer {

    /** At most this many clients are served at once; extra clients wait in a queue. */
    static final int WORKER_THREADS = 8;

    private final ServerSocket serverSocket;
    private final CommandRegistry registry;
    private final ExecutorService pool = Executors.newFixedThreadPool(WORKER_THREADS);

    /** Sockets of clients that are connected right now, so stop() can close them. */
    private final Set<Socket> openClients = ConcurrentHashMap.newKeySet();

    /**
     * Opens the listening socket straight away, so getPort() works before start().
     * Port 0 means "any free port", which the tests use.
     */
    public RedisServer(int port, CommandRegistry registry) throws IOException {
        this.registry = registry;
        this.serverSocket = new ServerSocket();
        // lets us restart the server at once without "address already in use"
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress(port));
    }

    /** The port the server is really listening on. */
    public int getPort() {
        return serverSocket.getLocalPort();
    }

    /**
     * Accepts clients until stop() is called. Each client is handed to the thread
     * pool, so this loop is free to accept the next client at once.
     * This method blocks the calling thread.
     */
    public void start() {
        while (!serverSocket.isClosed()) {
            try {
                Socket client = serverSocket.accept();
                openClients.add(client);
                pool.submit(() -> {
                    try {
                        new ClientHandler(client, registry).run();
                    } finally {
                        openClients.remove(client); // this client is finished
                    }
                });
            } catch (SocketException e) {
                // accept() throws this when stop() closes the server socket: time to finish
                break;
            } catch (IOException e) {
                System.err.println("Could not accept a client: " + e.getMessage());
            }
        }
    }

    /**
     * Stops the server: no new clients, connected clients are disconnected,
     * and the worker threads are shut down (waiting at most 5 seconds).
     */
    public void stop() {
        try {
            serverSocket.close();
        } catch (IOException e) {
            System.err.println("Error while closing the server: " + e.getMessage());
        }
        for (Socket client : openClients) {
            try {
                client.close(); // makes that client's readLine() fail, so its thread ends
            } catch (IOException e) {
                // already closing; nothing more to do
            }
        }
        pool.shutdown();
        try {
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
