package com.student.redis;

/**
 * TTL key: seconds until the key expires, -1 if it has no expiry,
 * -2 if the key does not exist.
 */
public class TtlCommand implements Command {

    private final Store store;

    public TtlCommand(Store store) {
        this.store = store;
    }

    @Override
    public String execute(String[] args) {
        if (args.length != 2) {
            return Replies.wrongArgs("TTL");
        }
        return Replies.integer(store.ttl(args[1]));
    }
}
