package com.student.redis;

import java.util.List;

/**
 * LRANGE key start stop: replies with the elements from start to stop (both
 * included) separated by spaces, or (empty list). Negative indexes count from
 * the end, so "LRANGE k 0 -1" shows the whole list.
 */
public class LrangeCommand implements Command {

    private final Store store;

    public LrangeCommand(Store store) {
        this.store = store;
    }

    @Override
    public String execute(String[] args) {
        if (args.length != 4) {
            return Replies.wrongArgs("LRANGE");
        }
        long start;
        long stop;
        try {
            start = Long.parseLong(args[2]);
            stop = Long.parseLong(args[3]);
        } catch (NumberFormatException e) {
            return Replies.NOT_AN_INTEGER;
        }
        try {
            List<String> elements = store.lrange(args[1], start, stop);
            if (elements.isEmpty()) {
                return "(empty list)";
            }
            return String.join(" ", elements);
        } catch (WrongTypeException e) {
            return e.getMessage();
        }
    }
}
