package com.student.demo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The three questions the shop's pages ask, each answered as a JSON string:
 * the home page, a list of products, and one product's page.
 *
 * Every answer starts with salesData.readStats(), which reads all the orders
 * again. So every page is slow to build, and worth keeping in the cache.
 */
public class ShopQueries {

    static final String ALL = "all";
    static final int BESTSELLER_COUNT = 8;
    static final int RELATED_COUNT = 4;

    private final SalesData salesData;

    public ShopQueries(SalesData salesData) {
        this.salesData = salesData;
    }

    /** True for "all" and for the web-address form of a real category, such as "water-bottle". */
    static boolean isKnownCategory(String slug) {
        if (ALL.equals(slug)) {
            return true;
        }
        for (String category : SalesData.categoryNames()) {
            if (ProductStats.slug(category).equals(slug)) {
                return true;
            }
        }
        return false;
    }

    /** Home page: shop-wide totals, the bestsellers, and one summary line per category. */
    public String home() throws IOException {
        List<ProductStats> ranked = salesData.readStats();

        long orders = 0;
        long unitsSold = 0;
        long revenue = 0;
        // LinkedHashMap keeps the categories in the order we first meet them (best seller first)
        Map<String, CategoryTotal> categories = new LinkedHashMap<>();
        for (ProductStats p : ranked) {
            orders += p.orders;
            unitsSold += p.unitsSold;
            revenue += p.revenue;

            CategoryTotal total = categories.get(p.category());
            if (total == null) {
                total = new CategoryTotal(p.category(), p.price);
                categories.put(p.category(), total);
            }
            total.add(p);
        }

        StringBuilder json = new StringBuilder();
        json.append("{\"orders\":").append(orders)
            .append(",\"unitsSold\":").append(unitsSold)
            .append(",\"revenue\":").append(revenue)
            .append(",\"productCount\":").append(ranked.size())
            .append(",\"bestsellers\":");
        appendProducts(json, ranked.subList(0, Math.min(BESTSELLER_COUNT, ranked.size())));
        json.append(",\"categories\":[");
        boolean first = true;
        for (CategoryTotal c : categories.values()) {
            if (!first) {
                json.append(",");
            }
            first = false;
            json.append("{\"name\":\"").append(escape(c.name)).append("\"")
                .append(",\"slug\":\"").append(escape(ProductStats.slug(c.name))).append("\"")
                .append(",\"productCount\":").append(c.productCount)
                .append(",\"unitsSold\":").append(c.unitsSold)
                .append(",\"fromPrice\":").append(c.lowestPrice)
                .append("}");
        }
        json.append("]}");
        return json.toString();
    }

    /** Product list: every product ("all") or the products of one category, best seller first. */
    public String products(String categorySlug) throws IOException {
        List<ProductStats> ranked = salesData.readStats();

        List<ProductStats> wanted = new ArrayList<>();
        String categoryName = "All products";
        for (ProductStats p : ranked) {
            if (ALL.equals(categorySlug)) {
                wanted.add(p);
            } else if (p.categorySlug().equals(categorySlug)) {
                wanted.add(p);
                categoryName = p.category();
            }
        }

        StringBuilder json = new StringBuilder();
        json.append("{\"category\":\"").append(escape(categorySlug)).append("\"")
            .append(",\"categoryName\":\"").append(escape(categoryName)).append("\"")
            .append(",\"products\":");
        appendProducts(json, wanted);
        json.append("}");
        return json.toString();
    }

    /**
     * Product page: one product with its order sizes, plus a few other products of
     * the same category. Returns null if no product in the sales file has this id.
     */
    public String product(int id) throws IOException {
        List<ProductStats> ranked = salesData.readStats();

        ProductStats product = null;
        for (ProductStats p : ranked) {
            if (p.id == id && id > 0) {
                product = p;
            }
        }
        if (product == null) {
            return null;
        }

        List<ProductStats> related = new ArrayList<>();
        for (ProductStats p : ranked) {
            if (p != product && p.category().equals(product.category()) && related.size() < RELATED_COUNT) {
                related.add(p);
            }
        }

        StringBuilder json = new StringBuilder();
        json.append("{\"productCount\":").append(ranked.size()).append(",\"product\":");
        appendProduct(json, product);
        json.append(",\"ordersByQuantity\":[");
        for (int i = 0; i < product.ordersByQuantity.length; i++) {
            if (i > 0) {
                json.append(",");
            }
            json.append(product.ordersByQuantity[i]);
        }
        json.append("],\"related\":");
        appendProducts(json, related);
        json.append("}");
        return json.toString();
    }

    /** Writes a JSON list of products: [{...},{...}] */
    private static void appendProducts(StringBuilder json, List<ProductStats> products) {
        json.append("[");
        for (int i = 0; i < products.size(); i++) {
            if (i > 0) {
                json.append(",");
            }
            appendProduct(json, products.get(i));
        }
        json.append("]");
    }

    /** Writes one product as a JSON object. */
    private static void appendProduct(StringBuilder json, ProductStats p) {
        json.append("{\"id\":").append(p.id)
            .append(",\"name\":\"").append(escape(p.name)).append("\"")
            .append(",\"style\":\"").append(escape(p.style())).append("\"")
            .append(",\"category\":\"").append(escape(p.category())).append("\"")
            .append(",\"slug\":\"").append(escape(p.categorySlug())).append("\"")
            .append(",\"price\":").append(p.price)
            .append(",\"orders\":").append(p.orders)
            .append(",\"unitsSold\":").append(p.unitsSold)
            .append(",\"revenue\":").append(p.revenue)
            .append(",\"rank\":").append(p.rank)
            .append("}");
    }

    /** Makes text safe inside a JSON string (backslash and double quote). */
    static String escape(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /** Running totals for one category while the home page is being built. */
    private static class CategoryTotal {
        final String name;
        int productCount;
        long unitsSold;
        long lowestPrice;

        CategoryTotal(String name, long firstPrice) {
            this.name = name;
            this.lowestPrice = firstPrice;
        }

        void add(ProductStats p) {
            productCount++;
            unitsSold += p.unitsSold;
            lowestPrice = Math.min(lowestPrice, p.price);
        }
    }
}
