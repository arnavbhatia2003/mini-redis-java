package com.student.demo;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The "slow database" of the demo shop.
 *
 * It keeps about 400,000 sales orders in a plain CSV file (demo-sales.csv).
 * readStats() reads the whole file every time it is called and works out the
 * totals of every product. That takes real time, which is the point: it stands
 * in for a slow database query that a cache can save us from.
 */
public class SalesData {

    public static final String DEFAULT_FILE = "demo-sales.csv";

    static final int ORDER_COUNT = 400_000;
    static final long SEED = 42L;
    static final String HEADER = "orderId,product,quantity,price";

    /** 20 styles x 10 categories = 200 product names. */
    private static final String[] STYLES = {
        "Red", "Blue", "Green", "Black", "White", "Silver", "Golden", "Orange", "Purple", "Yellow",
        "Small", "Large", "Classic", "Smart", "Mini", "Travel", "Office", "Budget", "Premium", "Everyday"
    };
    private static final String[] CATEGORIES = {
        "Backpack", "Notebook", "Water Bottle", "Desk Lamp", "Headphones",
        "Keyboard", "Wall Clock", "Coffee Mug", "Phone Case", "Umbrella"
    };

    private final Path file;

    public SalesData(Path file) {
        this.file = file;
    }

    public Path getFile() {
        return file;
    }

    /**
     * Writes the sales file if it is not there yet. Returns true if it was created.
     * The random numbers use a fixed seed, so every laptop gets exactly the same file.
     */
    public boolean createFileIfMissing() throws IOException {
        if (Files.exists(file)) {
            return false;
        }
        generate(file, ORDER_COUNT, SEED);
        return true;
    }

    /**
     * Writes a header line and then one line per order: orderId,product,quantity,price.
     * Each product has one fixed price (whole rupees). The file is written under a
     * temporary name first and renamed at the end, so a half-written file is never used.
     */
    static void generate(Path target, int orders, long seed) throws IOException {
        String[] names = productNames();
        Random random = new Random(seed);
        int[] prices = new int[names.length];
        for (int i = 0; i < prices.length; i++) {
            prices[i] = 100 + random.nextInt(4900); // 100 to 4999 rupees
        }

        Path folder = target.toAbsolutePath().getParent();
        Path temp = Files.createTempFile(folder, "demo-sales-", ".tmp");
        try (BufferedWriter out = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
            out.write(HEADER + "\n");
            for (int orderId = 1; orderId <= orders; orderId++) {
                int product = random.nextInt(names.length);
                int quantity = 1 + random.nextInt(5); // 1 to 5
                out.write(orderId + "," + names[product] + "," + quantity + "," + prices[product] + "\n");
            }
        }
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
    }

    /** The catalogue: 200 product names such as "Blue Backpack". Product 1 is the first name. */
    static String[] productNames() {
        String[] names = new String[STYLES.length * CATEGORIES.length];
        int next = 0;
        for (String style : STYLES) {
            for (String category : CATEGORIES) {
                names[next] = style + " " + category;
                next++;
            }
        }
        return names;
    }

    /** The 10 category names, for example "Water Bottle". */
    static String[] categoryNames() {
        return CATEGORIES.clone();
    }

    /**
     * The slow work: reads every order in the file and adds up the totals of each
     * product. Returns the products with the biggest revenue first; each one knows
     * its rank (1 = earned the most).
     *
     * @throws IOException if the file is missing or a line is not in the expected format
     */
    public List<ProductStats> readStats() throws IOException {
        Map<String, Integer> idByName = new HashMap<>();
        String[] names = productNames();
        for (int i = 0; i < names.length; i++) {
            idByName.put(names[i], i + 1);
        }

        Map<String, ProductStats> statsByName = new HashMap<>();
        try (BufferedReader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            int lineNumber = 1;
            String line = in.readLine(); // line 1 is the header, skip it
            line = in.readLine();
            while (line != null) {
                lineNumber++;
                if (!line.isBlank()) {
                    addOrder(statsByName, idByName, line, lineNumber);
                }
                line = in.readLine();
            }
        }

        List<ProductStats> ranked = new ArrayList<>(statsByName.values());
        // biggest revenue first; two products with equal revenue are ordered by name
        ranked.sort((a, b) -> {
            if (a.revenue != b.revenue) {
                return Long.compare(b.revenue, a.revenue);
            }
            return a.name.compareTo(b.name);
        });
        for (int i = 0; i < ranked.size(); i++) {
            ranked.get(i).rank = i + 1;
        }
        return ranked;
    }

    /** Adds one order line (orderId,product,quantity,price) to its product's totals. */
    private void addOrder(Map<String, ProductStats> statsByName, Map<String, Integer> idByName,
                          String line, int lineNumber) throws IOException {
        String[] parts = line.split(",");
        if (parts.length != 4) {
            throw new IOException(file + " line " + lineNumber + ": expected " + HEADER);
        }
        long quantity;
        long price;
        try {
            quantity = Long.parseLong(parts[2].trim());
            price = Long.parseLong(parts[3].trim());
        } catch (NumberFormatException e) {
            throw new IOException(file + " line " + lineNumber + ": quantity and price must be whole numbers");
        }

        String name = parts[1].trim();
        ProductStats stats = statsByName.get(name);
        if (stats == null) {
            Integer id = idByName.get(name);
            stats = new ProductStats(name, id == null ? 0 : id);
            statsByName.put(name, stats);
        }
        stats.addOrder(quantity, price);
    }
}
