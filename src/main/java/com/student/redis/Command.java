package com.student.redis;

import java.util.List;

/**
 * One Redis command, such as SET or GET.
 *
 * Every command class implements this interface, so CommandRegistry can run any
 * command the same way without knowing which one it is (polymorphism).
 */
public interface Command {

    /**
     * Runs the command and returns the one-line reply.
     *
     * @param args the words of the command line; args[0] is the command name (upper case)
     */
    String execute(String[] args);

    /**
     * True for commands that change data (SET, DEL, ...). Only these are saved to
     * the append-only file. Most commands only read, so the default is false.
     */
    default boolean isWrite() {
        return false;
    }

    /**
     * The line(s) to save in the append-only file after this command succeeded.
     * By default that is the command exactly as typed. Commands with a relative
     * expiry ("10 seconds from now") override this to save an exact time instead.
     */
    default List<String> linesToSave(String[] args) {
        return List.of(String.join(" ", args));
    }
}
