package com.student.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests the JSON answers of the shop's three pages on a tiny sales file, and the
 * small no-network helpers around them (addresses, cache keys, the request log,
 * the benchmark's page list, the files of the website).
 */
class ShopQueriesTest {

    @TempDir
    Path folder;

    private ShopQueries queries;

    @BeforeEach
    void writeATinySalesFile() throws IOException {
        Path file = folder.resolve("tiny.csv");
        Files.write(file, List.of(
                "orderId,product,quantity,price",
                "1,Red Backpack,2,100",      // product 1:  revenue 200
                "2,Blue Backpack,1,900",     // product 11: revenue 900
                "3,Red Notebook,3,50",       // product 2:  revenue 150
                "4,Red Backpack,1,100"));    // product 1:  revenue 300 in total
        queries = new ShopQueries(new SalesData(file));
    }

    @Test
    void homePageHasTotalsBestsellersAndCategories() throws IOException {
        String json = queries.home();

        assertTrue(json.startsWith("{\"orders\":4,\"unitsSold\":7,\"revenue\":1350,\"productCount\":3,"), json);
        // bestsellers are in revenue order: Blue Backpack (900), Red Backpack (300), Red Notebook (150)
        assertTrue(json.indexOf("Blue Backpack") < json.indexOf("Red Backpack"), json);
        assertTrue(json.indexOf("Red Backpack") < json.indexOf("Red Notebook"), json);
        assertTrue(json.contains("{\"name\":\"Backpack\",\"slug\":\"backpack\",\"productCount\":2,\"unitsSold\":4,\"fromPrice\":100}"), json);
        assertTrue(json.contains("{\"name\":\"Notebook\",\"slug\":\"notebook\",\"productCount\":1,\"unitsSold\":3,\"fromPrice\":50}"), json);
    }

    @Test
    void productListCanBeAllOrOneCategory() throws IOException {
        String all = queries.products("all");
        String backpacks = queries.products("backpack");

        assertTrue(all.startsWith("{\"category\":\"all\",\"categoryName\":\"All products\",\"products\":["), all);
        assertTrue(all.contains("Red Notebook"), all);
        assertTrue(backpacks.startsWith("{\"category\":\"backpack\",\"categoryName\":\"Backpack\","), backpacks);
        assertTrue(backpacks.contains("Blue Backpack"), backpacks);
        assertFalse(backpacks.contains("Red Notebook"), backpacks);
    }

    @Test
    void productPageHasTheProductItsOrderSizesAndRelatedProducts() throws IOException {
        String json = queries.product(1); // Red Backpack

        assertEquals("{\"productCount\":3,\"product\":"
                + "{\"id\":1,\"name\":\"Red Backpack\",\"style\":\"Red\",\"category\":\"Backpack\",\"slug\":\"backpack\","
                + "\"price\":100,\"orders\":2,\"unitsSold\":3,\"revenue\":300,\"rank\":2}"
                + ",\"ordersByQuantity\":[1,1,0,0,0]"
                + ",\"related\":[{\"id\":11,\"name\":\"Blue Backpack\",\"style\":\"Blue\",\"category\":\"Backpack\","
                + "\"slug\":\"backpack\",\"price\":900,\"orders\":1,\"unitsSold\":1,\"revenue\":900,\"rank\":1}]}", json);
    }

    @Test
    void aProductNobodyBoughtHasNoPage() throws IOException {
        assertNull(queries.product(200));
        assertNull(queries.product(0));
    }

    @Test
    void onlyRealCategoriesAreAccepted() {
        assertTrue(ShopQueries.isKnownCategory("all"));
        assertTrue(ShopQueries.isKnownCategory("water-bottle"));
        assertFalse(ShopQueries.isKnownCategory("Water Bottle"));
        assertFalse(ShopQueries.isKnownCategory("shoes"));
    }

    @Test
    void valuesAreReadFromTheAddress() {
        assertEquals("off", DemoApp.queryParam("id=7&cache=off", "cache"));
        assertEquals("7", DemoApp.queryParam("id=7&cache=off", "id"));
        assertNull(DemoApp.queryParam("id=7", "cache"));
        assertNull(DemoApp.queryParam(null, "cache"));
        assertNull(DemoApp.queryParam("mycache=on", "cache"));
    }

    @Test
    void thereIsOneCacheKeyPerPageAndEachIsOneWord() {
        List<String> keys = DemoApp.allCacheKeys();

        assertEquals(1 + 11 + 200, keys.size()); // home, 11 lists, 200 product pages
        assertTrue(keys.contains("shop:home"));
        assertTrue(keys.contains("shop:products:water-bottle"));
        assertTrue(keys.contains("shop:product:200"));
        for (String key : keys) {
            assertFalse(key.contains(" "), key);
        }
    }

    @Test
    void theRequestLogAnswersOnlyWithNewerRequests() {
        RequestLog log = new RequestLog();
        log.add("/api/home", "MISS", 131.5);
        log.add("/api/home", "HIT", 2.25);
        log.add("/api/product?id=7", "OFF", 120.0);

        String everything = log.jsonAfter(0);
        String newest = log.jsonAfter(2);

        assertTrue(everything.contains("\"last\":3"), everything);
        assertTrue(everything.contains(",\"/api/home\",\"MISS\",131.50]"), everything);
        assertTrue(everything.contains(",\"/api/home\",\"HIT\",2.25]"), everything);
        assertFalse(newest.contains("\"HIT\""), newest);
        assertTrue(newest.contains("[3,"), newest);
        assertTrue(newest.contains(",\"/api/product?id=7\",\"OFF\",120.00]"), newest);
        assertTrue(log.jsonAfter(3).endsWith("\"requests\":[]}"));
    }

    @Test
    void theRequestLogKeepsOnlyTheNewestRequests() {
        RequestLog log = new RequestLog();
        for (int i = 0; i < RequestLog.CAPACITY + 5; i++) {
            log.add("/api/home", "HIT", 1.0);
        }

        String json = log.jsonAfter(0);

        assertTrue(json.contains("\"last\":" + (RequestLog.CAPACITY + 5)), json.substring(0, 80));
        assertTrue(json.contains("\"requests\":[[6,"), "requests 1 to 5 were dropped");
    }

    @Test
    void theBenchmarkPicksDifferentProductPages() {
        List<String> pages = Benchmark.choosePages(20, new Random(1));

        assertEquals(1 + 11 + 20, pages.size());
        assertEquals("/api/home?page=home", pages.get(0));
        assertEquals(pages.size(), new java.util.HashSet<>(pages).size(), "no page is listed twice");
        assertEquals(1 + 11 + 200, Benchmark.choosePages(999, new Random(1)).size());
        assertEquals(2, Benchmark.countDifferent(new String[] {"/a", "/b", "/a", "/a"}));
    }

    @Test
    void everyPageOfTheWebsiteIsInsideTheJar() throws IOException {
        String[] addresses = {"/", "/products", "/product", "/analytics",
            "/shop.css", "/shop.js", "/analytics.css", "/analytics.js"};
        for (String address : addresses) {
            String file = StaticFiles.fileFor(address);
            assertTrue(StaticFiles.read(file).length > 100, file);
        }
        assertNull(StaticFiles.fileFor("/../pom.xml"));
        assertEquals("text/html; charset=utf-8", StaticFiles.contentType("index.html"));
        assertEquals("text/javascript; charset=utf-8", StaticFiles.contentType("shop.js"));
    }
}
