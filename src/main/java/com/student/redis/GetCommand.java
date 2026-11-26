package com.student.redis;

/** GET key: replies with the value, or (nil) if the key does not exist. */
public class GetCommand implements Command {

    private final Store store;

    public GetCommand(Store store) {
        this.store = store;
    }

    @Override
    public String execute(String[] args) {
        if (args.length != 2) {
            return Replies.wrongArgs("GET");
        }
        try {
            String value = store.get(args[1]);
            return value == null ? Replies.NIL : value;
        } catch (WrongTypeException e) {
            return e.getMessage();
        }
    }
}
