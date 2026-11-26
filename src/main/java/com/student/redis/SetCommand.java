package com.student.redis;

import java.time.Instant;
import java.util.List;

/**
 * SET key value [EX seconds]: stores a value, optionally expiring after some seconds.
 * Replies OK. A plain SET removes any old expiry on the key.
 */
public class SetCommand implements Command {

    private final Store store;

    public SetCommand(Store store) {
        this.store = store;
    }

    @Override
    public String execute(String[] args) {
        if (args.length == 3) {
            store.set(args[1], args[2]);
            return Replies.OK;
        }
        if (args.length == 5 && args[3].equalsIgnoreCase("EX")) {
            long seconds;
            try {
                seconds = Long.parseLong(args[4]);
            } catch (NumberFormatException e) {
                return Replies.NOT_AN_INTEGER;
            }
            if (seconds <= 0) {
                return "ERR invalid expire time in 'SET'";
            }
            store.set(args[1], args[2], seconds);
            return Replies.OK;
        }
        if (args.length < 3) {
            return Replies.wrongArgs("SET");
        }
        return "ERR syntax error";
    }

    @Override
    public boolean isWrite() {
        return true;
    }

    /**
     * "SET k v EX 10" is saved as "SET k v" plus "EXPIREAT k <exact time>",
     * so after a restart the key still expires at the original moment.
     */
    @Override
    public List<String> linesToSave(String[] args) {
        String plainSet = "SET " + args[1] + " " + args[2];
        Instant expiresAt = store.getExpiresAt(args[1]);
        if (args.length == 3 || expiresAt == null) {
            return List.of(plainSet);
        }
        return List.of(plainSet, "EXPIREAT " + args[1] + " " + expiresAt.getEpochSecond());
    }
}
