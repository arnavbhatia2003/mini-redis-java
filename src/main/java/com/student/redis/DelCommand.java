package com.student.redis;

/** DEL key: removes the key. Replies (integer) 1 if it existed, (integer) 0 if not. */
public class DelCommand implements Command {

    private final Store store;

    public DelCommand(Store store) {
        this.store = store;
    }

    @Override
    public String execute(String[] args) {
        if (args.length != 2) {
            return Replies.wrongArgs("DEL");
        }
        return Replies.integer(store.delete(args[1]) ? 1 : 0);
    }

    @Override
    public boolean isWrite() {
        return true;
    }
}
