package com.student.redis;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Tests for CommandRegistry: finding commands by name and handling bad input. */
class CommandRegistryTest {

    private final CommandRegistry registry = new CommandRegistry(new Store());

    @Test
    void runsCommandsByName() {
        assertEquals("OK", registry.execute("SET name alice"));
        assertEquals("alice", registry.execute("GET name"));
        assertEquals("(integer) 1", registry.execute("EXISTS name"));
        assertEquals("(integer) 1", registry.execute("DEL name"));
        assertEquals("(nil)", registry.execute("GET name"));
    }

    @Test
    void commandNamesAreCaseInsensitiveButKeysAreNot() {
        registry.execute("set Name alice");
        assertEquals("alice", registry.execute("get Name"));
        assertEquals("(nil)", registry.execute("GET name"));
    }

    @Test
    void extraSpacesAreIgnored() {
        assertEquals("OK", registry.execute("  SET   a    1  "));
        assertEquals("1", registry.execute("GET a"));
    }

    @Test
    void unknownCommandIsAnError() {
        assertEquals("ERR unknown command 'FLY'", registry.execute("fly away"));
    }

    @Test
    void emptyLineIsAnError() {
        assertEquals("ERR empty command", registry.execute("   "));
    }

    @Test
    void newCommandCanBeRegisteredWithoutChangingOtherCode() {
        registry.register("PING", args -> "PONG");
        assertEquals("PONG", registry.execute("ping"));
    }
}
