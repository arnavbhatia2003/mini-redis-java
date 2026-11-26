package com.student.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Tests the parts of RedisCache that need no network: the Base64 round trip
 * and the checks on keys and values.
 */
class RedisCacheTest {

    @Test
    void jsonWithSpacesSurvivesTheRoundTrip() {
        String json = "{\"ordersRead\":3,\"products\":[{\"rank\":1,\"product\":\"Blue Water Bottle\"}]}";

        String encoded = RedisCache.encode(json);

        assertEquals(json, RedisCache.decode(encoded));
    }

    @Test
    void encodedTextIsOneProtocolWord() {
        String encoded = RedisCache.encode("two words\tand a tab\nand a new line");

        // one word: no space, tab or line break anywhere, so "SET key <encoded>" stays 3 words
        assertEquals(1, encoded.split("\\s+").length);
        assertFalse(encoded.contains(" "));
        assertFalse(encoded.contains("\n"));
        assertEquals("two words\tand a tab\nand a new line", RedisCache.decode(encoded));
    }

    @Test
    void lettersOutsideEnglishSurviveTheRoundTrip() {
        String text = "नमस्ते दुनिया, price ₹499, café";

        assertEquals(text, RedisCache.decode(RedisCache.encode(text)));
    }

    @Test
    void aKnownValueEncodesAsExpected() {
        assertEquals("aGVsbG8gd29ybGQ=", RedisCache.encode("hello world"));
        assertEquals("hello world", RedisCache.decode("aGVsbG8gd29ybGQ="));
    }

    @Test
    void aKeyWithASpaceIsRefusedBeforeAnythingIsSent() {
        // port 1 is never used here: the check happens before any connection is opened
        RedisCache cache = new RedisCache("localhost", 1);

        assertThrows(IllegalArgumentException.class, () -> cache.get("two words"));
        assertThrows(IllegalArgumentException.class, () -> cache.put("", "value", 30));
        assertThrows(IllegalArgumentException.class, () -> cache.put("key", "", 30));
        assertThrows(IllegalArgumentException.class, () -> cache.delete("tab\tkey"));
    }

    @Test
    void deletingManyKeysChecksEveryKeyFirst() {
        RedisCache cache = new RedisCache("localhost", 1);

        assertThrows(IllegalArgumentException.class,
                () -> cache.deleteAll(java.util.List.of("shop:home", "two words")));
    }
}
