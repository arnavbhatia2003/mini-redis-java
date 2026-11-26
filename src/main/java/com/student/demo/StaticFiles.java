package com.student.demo;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * The fixed files of the website: the HTML pages, the style sheets and the
 * scripts. They live in src/main/resources/site and are packed into the jar,
 * so the website needs no extra folder and no internet.
 *
 * Only the addresses listed here are served. Nothing else on the laptop can be
 * read through the website.
 */
public final class StaticFiles {

    /** Web address -> file name inside the "site" folder. */
    private static final Map<String, String> FILES = Map.of(
            "/", "index.html",
            "/products", "products.html",
            "/product", "product.html",
            "/analytics", "analytics.html",
            "/shop.css", "shop.css",
            "/shop.js", "shop.js",
            "/analytics.css", "analytics.css",
            "/analytics.js", "analytics.js");

    private StaticFiles() {
        // only static helpers, no objects
    }

    /** The file for a web address, or null if the address is not one of ours. */
    static String fileFor(String path) {
        return FILES.get(path);
    }

    /** Tells the browser what kind of file it is getting. */
    static String contentType(String fileName) {
        if (fileName.endsWith(".html")) {
            return "text/html; charset=utf-8";
        }
        if (fileName.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }
        if (fileName.endsWith(".js")) {
            return "text/javascript; charset=utf-8";
        }
        return "application/octet-stream";
    }

    /** Reads a file of the "site" folder from inside the jar. */
    static byte[] read(String fileName) throws IOException {
        try (InputStream in = StaticFiles.class.getResourceAsStream("/site/" + fileName)) {
            if (in == null) {
                throw new IOException("the website file " + fileName + " is missing from the jar");
            }
            return in.readAllBytes();
        }
    }
}
