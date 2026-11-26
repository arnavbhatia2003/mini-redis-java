package com.student.demo;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests the slow calculation (totals and ranking of every product) on tiny files, with no network. */
class SalesDataTest {

    @TempDir
    Path folder;

    private SalesData salesDataWith(String... lines) throws IOException {
        Path file = folder.resolve("tiny.csv");
        Files.write(file, List.of(lines));
        return new SalesData(file);
    }

    @Test
    void addsUpEachProductAndPutsTheBiggestRevenueFirst() throws IOException {
        SalesData data = salesDataWith(
                "orderId,product,quantity,price",
                "1,Blue Backpack,2,10",   // Blue Backpack: 2 * 10  = 20
                "2,Red Notebook,1,500",   // Red Notebook:  1 * 500 = 500
                "3,Blue Backpack,3,10",   // Blue Backpack: 3 * 10  = 30  -> 50 in total
                "4,Mini Umbrella,2,150"); // Mini Umbrella: 2 * 150 = 300

        List<ProductStats> ranked = data.readStats();

        assertEquals(3, ranked.size());
        assertEquals("Red Notebook", ranked.get(0).name);
        assertEquals(1, ranked.get(0).rank);
        assertEquals("Mini Umbrella", ranked.get(1).name);
        assertEquals(2, ranked.get(1).rank);

        ProductStats backpack = ranked.get(2);
        assertEquals("Blue Backpack", backpack.name);
        assertEquals(3, backpack.rank);
        assertEquals(2, backpack.orders);
        assertEquals(5, backpack.unitsSold);
        assertEquals(50, backpack.revenue);
        assertEquals(10, backpack.price);
    }

    @Test
    void productsWithEqualRevenueAreOrderedByName() throws IOException {
        SalesData data = salesDataWith(
                "orderId,product,quantity,price",
                "1,Red Umbrella,1,100",
                "2,Blue Backpack,2,50");

        List<ProductStats> ranked = data.readStats();

        assertEquals("Blue Backpack", ranked.get(0).name);
        assertEquals("Red Umbrella", ranked.get(1).name);
    }

    @Test
    void ordersAreCountedBySize() throws IOException {
        SalesData data = salesDataWith(
                "orderId,product,quantity,price",
                "1,Blue Backpack,1,10",
                "2,Blue Backpack,1,10",
                "3,Blue Backpack,3,10",
                "4,Blue Backpack,9,10"); // 9 pieces counts in the last group, "5 or more"

        ProductStats backpack = data.readStats().get(0);

        assertArrayEquals(new long[] {2, 0, 1, 0, 1}, backpack.ordersByQuantity);
    }

    @Test
    void aProductKnowsItsNumberStyleAndCategory() throws IOException {
        SalesData data = salesDataWith(
                "orderId,product,quantity,price",
                "1,Red Backpack,1,10",       // the first name in the catalogue
                "2,Blue Water Bottle,1,5",   // style 2 (Blue), category 3 (Water Bottle) -> number 13
                "3,Pen,1,1");                // not in the catalogue

        List<ProductStats> ranked = data.readStats();

        assertEquals(1, ranked.get(0).id);
        ProductStats bottle = ranked.get(1);
        assertEquals(13, bottle.id);
        assertEquals("Blue", bottle.style());
        assertEquals("Water Bottle", bottle.category());
        assertEquals("water-bottle", bottle.categorySlug());
        assertEquals(0, ranked.get(2).id);
        assertEquals("Pen", ranked.get(2).category());
    }

    @Test
    void aFileWithOnlyTheHeaderGivesAnEmptyList() throws IOException {
        SalesData data = salesDataWith("orderId,product,quantity,price");

        assertTrue(data.readStats().isEmpty());
    }

    @Test
    void aBrokenLineIsReportedWithItsLineNumber() throws IOException {
        SalesData data = salesDataWith(
                "orderId,product,quantity,price",
                "1,Blue Backpack,2,10",
                "2,Blue Backpack,many,10");

        IOException error = assertThrows(IOException.class, data::readStats);

        assertTrue(error.getMessage().contains("line 3"), error.getMessage());
    }

    @Test
    void generatedFileHasOneLinePerOrderAndIsTheSameEveryTime() throws IOException {
        Path first = folder.resolve("first.csv");
        Path second = folder.resolve("second.csv");

        SalesData.generate(first, 500, 42L);
        SalesData.generate(second, 500, 42L);

        List<String> lines = Files.readAllLines(first);
        assertEquals(501, lines.size()); // header + 500 orders
        assertEquals("orderId,product,quantity,price", lines.get(0));
        assertEquals(4, lines.get(500).split(",").length);
        assertEquals(lines, Files.readAllLines(second)); // same seed, same file

        long orders = 0;
        for (ProductStats p : new SalesData(first).readStats()) {
            orders += p.orders;
            assertTrue(p.id >= 1 && p.id <= 200, "every generated product is in the catalogue");
        }
        assertEquals(500, orders);
    }

    @Test
    void thereAreTwoHundredDifferentProductNames() {
        String[] names = SalesData.productNames();

        assertEquals(200, names.length);
        assertEquals(200, java.util.Set.of(names).size()); // Set.of refuses duplicates
        assertEquals(10, SalesData.categoryNames().length);
    }

    @Test
    void anExistingFileIsNotWrittenAgain() throws IOException {
        SalesData data = salesDataWith("orderId,product,quantity,price", "1,Blue Backpack,1,10");

        assertFalse(data.createFileIfMissing());
        assertEquals(2, Files.readAllLines(data.getFile()).size());
    }
}
