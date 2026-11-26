package com.student.redis;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Knows every command by name and runs a command line.
 *
 * To add a command you write one new class and register it here. No existing
 * code has to change. If an AppendOnlyFile is set, successful write commands
 * are saved to it.
 */
public class CommandRegistry {

    private final Map<String, Command> commands = new HashMap<>();
    private final Object writeLock = new Object();
    private AppendOnlyFile appendOnlyFile; // null means "don't save to disk"

    /** Creates a registry with all the commands, working on the given store. */
    public CommandRegistry(Store store) {
        register("SET", new SetCommand(store));
        register("GET", new GetCommand(store));
        register("DEL", new DelCommand(store));
        register("EXISTS", new ExistsCommand(store));
        register("TTL", new TtlCommand(store));
        register("EXPIRE", new ExpireCommand(store));
        register("EXPIREAT", new ExpireAtCommand(store));
        register("LPUSH", new LpushCommand(store));
        register("RPUSH", new RpushCommand(store));
        register("LPOP", new LpopCommand(store));
        register("LRANGE", new LrangeCommand(store));
    }

    /** Adds a command under its (upper-case) name. */
    public void register(String name, Command command) {
        commands.put(name.toUpperCase(), command);
    }

    /** From now on, successful write commands are saved to this file. */
    public void setAppendOnlyFile(AppendOnlyFile appendOnlyFile) {
        this.appendOnlyFile = appendOnlyFile;
    }

    /**
     * Runs one command line such as "SET name alice" and returns the reply line.
     * Command names are case-insensitive; keys and values are not.
     */
    public String execute(String line) {
        return run(line, true);
    }

    /** Runs a line without saving it. Used while replaying the file at startup. */
    public String executeWithoutSaving(String line) {
        return run(line, false);
    }

    private String run(String line, boolean save) {
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            return "ERR empty command";
        }
        String[] args = trimmed.split("\\s+");
        args[0] = args[0].toUpperCase();

        Command command = commands.get(args[0]);
        if (command == null) {
            return "ERR unknown command '" + args[0] + "'";
        }
        if (!save || !command.isWrite() || appendOnlyFile == null) {
            return command.execute(args);
        }

        // Run the write and save it as one step, so when two clients write at the
        // same time the file lists the writes in the same order the store saw them.
        synchronized (writeLock) {
            String reply = command.execute(args);
            if (!Replies.isError(reply)) {
                try {
                    appendOnlyFile.append(command.linesToSave(args));
                } catch (IOException e) {
                    System.err.println("Could not save to " + appendOnlyFile.getPath() + ": " + e.getMessage());
                }
            }
            return reply;
        }
    }
}
