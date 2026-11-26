package com.student.redis;

import java.util.ArrayList;
import java.util.List;

/**
 * LPUSH key value [value ...]: adds values to the front of a list, one at a time,
 * so "LPUSH k a b c" gives the list c b a. Replies with the new length.
 */
public class LpushCommand implements Command {

    private final Store store;

    public LpushCommand(Store store) {
        this.store = store;
    }

    @Override
    public String execute(String[] args) {
        if (args.length < 3) {
            return Replies.wrongArgs("LPUSH");
        }
        List<String> values = new ArrayList<>();
        for (int i = 2; i < args.length; i++) {
            values.add(args[i]);
        }
        try {
            return Replies.integer(store.lpush(args[1], values));
        } catch (WrongTypeException e) {
            return e.getMessage();
        }
    }

    @Override
    public boolean isWrite() {
        return true;
    }
}
