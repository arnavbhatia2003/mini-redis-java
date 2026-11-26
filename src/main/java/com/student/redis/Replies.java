package com.student.redis;

/**
 * Small helpers that build reply lines in the same style redis-cli prints,
 * so every command formats its replies the same way.
 */
public final class Replies {

    public static final String OK = "OK";
    public static final String NIL = "(nil)";
    public static final String NOT_AN_INTEGER = "ERR value is not an integer or out of range";

    private Replies() {
        // only static helpers, no objects
    }

    /** A number reply, e.g. "(integer) 3". */
    public static String integer(long n) {
        return "(integer) " + n;
    }

    /** True if the reply is an error (those commands are not saved to the file). */
    public static boolean isError(String reply) {
        return reply.startsWith("ERR") || reply.startsWith("WRONGTYPE");
    }

    /** The error for a command called with the wrong number of words. */
    public static String wrongArgs(String commandName) {
        return "ERR wrong number of arguments for '" + commandName + "'";
    }
}
