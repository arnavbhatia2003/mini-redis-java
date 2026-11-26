package com.student.demo;

/**
 * Thrown by RedisCache when the mini-redis server cannot be used right now:
 * it is not running, it did not answer in time, or it sent an error reply.
 * The website catches this and simply does the slow work instead.
 */
public class CacheUnavailableException extends Exception {

    public CacheUnavailableException(String message) {
        super(message);
    }

    public CacheUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
