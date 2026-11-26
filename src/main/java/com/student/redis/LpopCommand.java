package com.student.redis;

/** LPOP key: removes and replies with the first element of a list, or (nil) if there is none. */
public class LpopCommand implements Command {

    private final Store store;

    public LpopCommand(Store store) {
        this.store = store;
    }

    @Override
    public String execute(String[] args) {
        if (args.length != 2) {
            return Replies.wrongArgs("LPOP");
        }
        try {
            String first = store.lpop(args[1]);
            return first == null ? Replies.NIL : first;
        } catch (WrongTypeException e) {
            return e.getMessage();
        }
    }

    @Override
    public boolean isWrite() {
        return true;
    }
}
