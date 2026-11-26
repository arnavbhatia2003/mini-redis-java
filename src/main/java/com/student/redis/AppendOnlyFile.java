package com.student.redis;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

/**
 * Persistence: every successful write command is added as one line at the end
 * of a text file (data.aof). On startup the file is read back and every line is
 * run again, which rebuilds the data exactly as it was.
 */
public class AppendOnlyFile {

    private final Path path;

    public AppendOnlyFile(Path path) {
        this.path = path;
    }

    public Path getPath() {
        return path;
    }

    /**
     * Adds the lines to the end of the file, creating it if needed.
     * synchronized: two threads must never write into the file at the same time.
     */
    public synchronized void append(List<String> lines) throws IOException {
        StringBuilder text = new StringBuilder();
        for (String line : lines) {
            text.append(line).append('\n');
        }
        Files.writeString(path, text.toString(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    /**
     * Runs every saved line again through the registry, without saving them a
     * second time. A missing file just means there is nothing to load.
     *
     * @return how many commands were replayed
     */
    public int replay(CommandRegistry registry) throws IOException {
        if (!Files.exists(path)) {
            return 0;
        }
        int replayed = 0;
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            String reply = registry.executeWithoutSaving(line);
            if (Replies.isError(reply)) {
                // a damaged line should not stop the server from starting
                System.err.println("Skipping bad line in " + path + ": " + line + " (" + reply + ")");
            } else {
                replayed++;
            }
        }
        return replayed;
    }
}
