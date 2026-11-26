package com.student.demo;

import java.util.Locale;

/**
 * Everything the shop knows about one product after reading the sales file:
 * its price, how many orders and pieces were sold, the money it earned, and
 * its rank among all products (1 = earned the most).
 *
 * A product name is two parts, for example "Blue Water Bottle": the first word
 * is its style ("Blue"), the rest is its category ("Water Bottle").
 */
public class ProductStats {

    /** Orders are counted by how many pieces they had: 1, 2, 3, 4, and "5 or more". */
    static final int QUANTITY_GROUPS = 5;

    final String name;
    final int id; // 1 to 200 = position in the catalogue, 0 = not in the catalogue
    long price;
    long orders;
    long unitsSold;
    long revenue;
    final long[] ordersByQuantity = new long[QUANTITY_GROUPS];
    int rank;

    ProductStats(String name, int id) {
        this.name = name;
        this.id = id;
    }

    /** Adds one order of this product to the totals. */
    void addOrder(long quantity, long priceOfOne) {
        price = priceOfOne;
        orders++;
        unitsSold += quantity;
        revenue += quantity * priceOfOne;

        int group = (int) Math.min(quantity, QUANTITY_GROUPS) - 1; // 1 piece -> 0 ... 5 or more -> 4
        if (group >= 0) {
            ordersByQuantity[group]++;
        }
    }

    /** The first word of the name, for example "Blue". Empty if the name is one word. */
    String style() {
        int space = name.indexOf(' ');
        return space < 0 ? "" : name.substring(0, space);
    }

    /** The name without its first word, for example "Water Bottle". */
    String category() {
        int space = name.indexOf(' ');
        return space < 0 ? name : name.substring(space + 1);
    }

    /** The category as it is written in a web address and in a cache key: "water-bottle". */
    String categorySlug() {
        return slug(category());
    }

    /** Lower case with a dash instead of each space, so the text is one word. */
    static String slug(String text) {
        return text.toLowerCase(Locale.ROOT).replace(' ', '-');
    }
}
