package com.student.redis;

/** EXISTS key: replies (integer) 1 if the key exists, (integer) 0 if not. */
public class ExistsCommand implements Command {

    private final Store store;

    public ExistsCommand(Store store) {
        this.store = store;
    }

    @Override
    public String execute(String[] args) {
        if (args.length != 2) {
            return Replies.wrongArgs("EXISTS");
        }
        return Replies.integer(store.exists(args[1]) ? 1 : 0);
    }
}
