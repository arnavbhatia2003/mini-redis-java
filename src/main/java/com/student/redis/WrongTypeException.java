package com.student.redis;

/**
 * Thrown when a command is used on the wrong kind of value, for example GET on a
 * list or LPUSH on a string. Commands turn it into the WRONGTYPE error reply.
 */
public class WrongTypeException extends RuntimeException {

    public static final String MESSAGE = "WRONGTYPE Operation against a key holding the wrong kind of value";

    public WrongTypeException() {
        super(MESSAGE);
    }
}
