package com.student.redis;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Starts the mini Redis server.
 *
 * Usage: java -jar target/mini-redis-1.0.jar [--port 6379] [--aof data.aof]
 *
 * On startup it first replays the append-only file, so data from the last run comes back.
 */
public class Main {

    static final int DEFAULT_PORT = 6379;
    static final String DEFAULT_AOF = "data.aof";

    public static void main(String[] args) throws IOException {
        int port = DEFAULT_PORT;
        String aofPath = DEFAULT_AOF;
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals("--port")) {
                port = Integer.parseInt(args[i + 1]);
                i++; // skip the value we just read
            } else if (args[i].equals("--aof")) {
                aofPath = args[i + 1];
                i++;
            }
        }

        CommandRegistry registry = new CommandRegistry(new Store());

        // 1. rebuild the data from the file, 2. then start saving new writes to it
        AppendOnlyFile appendOnlyFile = new AppendOnlyFile(Path.of(aofPath));
        int replayed = appendOnlyFile.replay(registry);
        System.out.println("Loaded " + replayed + " commands from " + aofPath);
        registry.setAppendOnlyFile(appendOnlyFile);

        RedisServer server = new RedisServer(port, registry);
        System.out.println("mini-redis listening on port " + server.getPort());
        server.start();
    }
}
