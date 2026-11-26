package com.student.redis;

import java.time.Instant;

/**
 * EXPIREAT key unix-seconds: makes an existing key expire at an exact moment
 * (seconds since 1 Jan 1970 UTC). Replies (integer) 1 if the key exists, 0 if not.
 *
 * The append-only file saves every expiry in this form, so a restart doesn't
 * give keys a fresh "10 seconds from now".
 */
public class ExpireAtCommand implements Command {

    private final Store store;

    public ExpireAtCommand(Store store) {
        this.store = store;
    }

    @Override
    public String execute(String[] args) {
        if (args.length != 3) {
            return Replies.wrongArgs("EXPIREAT");
        }
        long unixSeconds;
        try {
            unixSeconds = Long.parseLong(args[2]);
        } catch (NumberFormatException e) {
            return Replies.NOT_AN_INTEGER;
        }
        return Replies.integer(store.expireAt(args[1], Instant.ofEpochSecond(unixSeconds)) ? 1 : 0);
    }

    @Override
    public boolean isWrite() {
        return true;
    }
}
