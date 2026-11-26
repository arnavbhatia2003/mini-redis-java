package com.student.redis;

import java.time.Instant;
import java.util.List;

/**
 * EXPIRE key seconds: makes an existing key expire after that many seconds.
 * Replies (integer) 1 if the key exists, (integer) 0 if it does not.
 */
public class ExpireCommand implements Command {

    private final Store store;

    public ExpireCommand(Store store) {
        this.store = store;
    }

    @Override
    public String execute(String[] args) {
        if (args.length != 3) {
            return Replies.wrongArgs("EXPIRE");
        }
        long seconds;
        try {
            seconds = Long.parseLong(args[2]);
        } catch (NumberFormatException e) {
            return Replies.NOT_AN_INTEGER;
        }
        return Replies.integer(store.expire(args[1], seconds) ? 1 : 0);
    }

    @Override
    public boolean isWrite() {
        return true;
    }

    /** Saves the exact expiry moment (EXPIREAT), not "n seconds from now". */
    @Override
    public List<String> linesToSave(String[] args) {
        Instant expiresAt = store.getExpiresAt(args[1]);
        if (expiresAt == null) {
            // the key is gone (it never existed, or EXPIRE 0 removed it at once)
            return List.of("DEL " + args[1]);
        }
        return List.of("EXPIREAT " + args[1] + " " + expiresAt.getEpochSecond());
    }
}
