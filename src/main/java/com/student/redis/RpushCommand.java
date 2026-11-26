package com.student.redis;

import java.util.ArrayList;
import java.util.List;

/**
 * RPUSH key value [value ...]: adds values to the end of a list, so
 * "RPUSH k a b c" gives the list a b c. Replies with the new length.
 */
public class RpushCommand implements Command {

    private final Store store;

    public RpushCommand(Store store) {
        this.store = store;
    }

    @Override
    public String execute(String[] args) {
        if (args.length < 3) {
            return Replies.wrongArgs("RPUSH");
        }
        List<String> values = new ArrayList<>();
        for (int i = 2; i < args.length; i++) {
            values.add(args[i]);
        }
        try {
            return Replies.integer(store.rpush(args[1], values));
        } catch (WrongTypeException e) {
            return e.getMessage();
        }
    }

    @Override
    public boolean isWrite() {
        return true;
    }
}
