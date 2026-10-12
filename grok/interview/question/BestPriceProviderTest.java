package interview.question;

/**
 * Simple manual test harness (no external test framework required).
 * Run the main method to exercise the BestPriceProvider.
 */
public class BestPriceProviderTest {

    public static void main(String[] args) {
        BestPriceProvider provider = new BestPriceProvider();

        System.out.println("=== Basic correctness ===");
        provider.priceUpdate(Shop.STARBUCKS, Commodity.COFFEE, 4.50);
        provider.priceUpdate(Shop.TOAST_BOX, Commodity.COFFEE, 3.20);
        provider.priceUpdate(Shop.COFFEE_BEAN, Commodity.COFFEE, 5.10);

        assertEquals(3.20, provider.minPrice(Commodity.COFFEE), "min coffee");
        assertEquals(5.10, provider.maxPrice(Commodity.COFFEE), "max coffee");

        // Update that improves the min
        provider.priceUpdate(Shop.KOMALA_VILAS, Commodity.COFFEE, 2.90);
        assertEquals(2.90, provider.minPrice(Commodity.COFFEE), "new min coffee");

        // Update that improves the max
        provider.priceUpdate(Shop.STARBUCKS, Commodity.COFFEE, 6.00);
        assertEquals(6.00, provider.maxPrice(Commodity.COFFEE), "new max coffee");

        // Different commodity is independent
        provider.priceUpdate(Shop.STARBUCKS, Commodity.TEA, 3.00);
        assertEquals(3.00, provider.minPrice(Commodity.TEA), "min tea");
        assertEquals(3.00, provider.maxPrice(Commodity.TEA), "max tea");
        // Coffee should be unaffected
        assertEquals(2.90, provider.minPrice(Commodity.COFFEE), "coffee still min");

        System.out.println("=== Edge cases ===");
        // No prices reported yet
        assertEquals(Double.POSITIVE_INFINITY, provider.minPrice(Commodity.ICE_TEA), "no min");
        assertEquals(Double.NEGATIVE_INFINITY, provider.maxPrice(Commodity.ICE_TEA), "no max");

        // Same price from multiple shops
        provider.priceUpdate(Shop.TOAST_BOX, Commodity.ICE_COFFEE, 4.00);
        provider.priceUpdate(Shop.STARBUCKS, Commodity.ICE_COFFEE, 4.00);
        assertEquals(4.00, provider.minPrice(Commodity.ICE_COFFEE), "same min");
        assertEquals(4.00, provider.maxPrice(Commodity.ICE_COFFEE), "same max");

        System.out.println("=== Concurrent updates (smoke test) ===");
        Thread[] threads = new Thread[8];
        for (int i = 0; i < threads.length; i++) {
            final int id = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 1000; j++) {
                    Shop shop = Shop.values()[id % Shop.values().length];
                    Commodity c = Commodity.values()[j % Commodity.values().length];
                    double price = 1.0 + (id * 0.1) + (j % 50) * 0.01;
                    provider.priceUpdate(shop, c, price);
                }
            });
            threads[i].start();
        }
        for (Thread t : threads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        // Just verify we can still query without exception
        for (Commodity c : Commodity.values()) {
            double min = provider.minPrice(c);
            double max = provider.maxPrice(c);
            System.out.printf("%s  min=%.2f  max=%.2f%n", c, min, max);
            if (min > max && min != Double.POSITIVE_INFINITY) {
                throw new AssertionError("min > max for " + c);
            }
        }

        System.out.println("\nAll tests passed.");
    }

    private static void assertEquals(double expected, double actual, String msg) {
        if (Double.compare(expected, actual) != 0) {
            throw new AssertionError(msg + " - expected " + expected + " but was " + actual);
        }
        System.out.println("OK: " + msg + " = " + actual);
    }
}
