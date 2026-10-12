package interview.question;

import java.util.NoSuchElementException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Dependency-free tests: throws AssertionError on failure; no -ea flag needed. */
public class BestPriceProviderTest {
    public static void main(String[] args) throws Exception {
        emptyQuotes();
        replacementAndTies();
        independentCommodities();
        invalidInputs();
        zeroPrice();
        concurrentUpdatesAndQueries();
        System.out.println("PASS: all 6 test groups, including concurrent updates and queries");
    }

    private static void emptyQuotes() {
        BestPriceProvider p = new BestPriceProvider();
        expect(NoSuchElementException.class, () -> p.minPrice(Commodity.COFFEE));
        expect(NoSuchElementException.class, () -> p.maxPrice(Commodity.COFFEE));
    }

    private static void replacementAndTies() {
        BestPriceProvider p = new BestPriceProvider();
        p.priceUpdate(Shop.TOAST_BOX, Commodity.COFFEE, 2.0);
        equal(2.0, p.minPrice(Commodity.COFFEE));
        equal(2.0, p.maxPrice(Commodity.COFFEE));
        p.priceUpdate(Shop.STARBUCKS, Commodity.COFFEE, 5.0);
        p.priceUpdate(Shop.COFFEE_BEAN, Commodity.COFFEE, 2.0);
        p.priceUpdate(Shop.TOAST_BOX, Commodity.COFFEE, 6.0);
        equal(2.0, p.minPrice(Commodity.COFFEE));
        equal(6.0, p.maxPrice(Commodity.COFFEE));
        p.priceUpdate(Shop.COFFEE_BEAN, Commodity.COFFEE, 7.0);
        equal(5.0, p.minPrice(Commodity.COFFEE));
        equal(7.0, p.maxPrice(Commodity.COFFEE));
        p.priceUpdate(Shop.COFFEE_BEAN, Commodity.COFFEE, 3.0);
        equal(3.0, p.minPrice(Commodity.COFFEE));
        equal(6.0, p.maxPrice(Commodity.COFFEE));
        p.priceUpdate(Shop.TOAST_BOX, Commodity.COFFEE, 3.0);
        p.priceUpdate(Shop.STARBUCKS, Commodity.COFFEE, 3.0);
        p.priceUpdate(Shop.STARBUCKS, Commodity.COFFEE, 3.0);
        equal(3.0, p.minPrice(Commodity.COFFEE));
        equal(3.0, p.maxPrice(Commodity.COFFEE));
    }

    private static void independentCommodities() {
        BestPriceProvider p = new BestPriceProvider();
        for (Commodity c : Commodity.values()) {
            p.priceUpdate(Shop.KOMALA_VILAS, c, c.ordinal() + 1.0);
        }
        for (Commodity c : Commodity.values()) {
            equal(c.ordinal() + 1.0, p.minPrice(c));
            equal(c.ordinal() + 1.0, p.maxPrice(c));
        }
    }

    private static void invalidInputs() {
        BestPriceProvider p = new BestPriceProvider();
        p.priceUpdate(Shop.TOAST_BOX, Commodity.TEA, 4.0);
        for (double price : new double[]{-1, Double.NaN, Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY}) {
            expect(IllegalArgumentException.class,
                    () -> p.priceUpdate(Shop.TOAST_BOX, Commodity.TEA, price));
        }
        equal(4.0, p.minPrice(Commodity.TEA));
        equal(4.0, p.maxPrice(Commodity.TEA));
        expect(NullPointerException.class, () -> p.priceUpdate(null, Commodity.TEA, 1));
        expect(NullPointerException.class, () -> p.priceUpdate(Shop.TOAST_BOX, null, 1));
        expect(NullPointerException.class, () -> p.minPrice(null));
        expect(NullPointerException.class, () -> p.maxPrice(null));
    }

    private static void zeroPrice() {
        BestPriceProvider p = new BestPriceProvider();
        p.priceUpdate(Shop.TOAST_BOX, Commodity.ICE_TEA, -0.0);
        equal(0.0, p.minPrice(Commodity.ICE_TEA));
        equal(0.0, p.maxPrice(Commodity.ICE_TEA));
    }

    private static void concurrentUpdatesAndQueries() throws Exception {
        BestPriceProvider p = new BestPriceProvider();
        for (Shop s : Shop.values()) {
            p.priceUpdate(s, Commodity.COFFEE, 1.0);
        }
        ExecutorService pool = Executors.newFixedThreadPool(6);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> tasks = new ArrayList<>();
        try {
            for (Shop s : Shop.values()) {
                tasks.add(pool.submit(() -> {
                    await(start);
                    for (int i = 0; i < 10_000; i++) {
                        p.priceUpdate(s, Commodity.COFFEE, (i % 100) + 1.0);
                    }
                    p.priceUpdate(s, Commodity.COFFEE, s.ordinal() + 10.0);
                }));
            }
            for (int reader = 0; reader < 2; reader++) {
                tasks.add(pool.submit(() -> {
                    await(start);
                    for (int i = 0; i < 10_000; i++) {
                        inRange(p.minPrice(Commodity.COFFEE));
                        inRange(p.maxPrice(Commodity.COFFEE));
                    }
                }));
            }
            start.countDown();
            for (Future<?> task : tasks) {
                task.get(30, TimeUnit.SECONDS);
            }
            equal(10.0, p.minPrice(Commodity.COFFEE));
            equal(13.0, p.maxPrice(Commodity.COFFEE));
        } finally {
            pool.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    private static void inRange(double value) {
        if (!Double.isFinite(value) || value < 1.0 || value > 100.0) {
            throw new AssertionError("Invalid concurrent quote: " + value);
        }
    }

    private static void equal(double expected, double actual) {
        if (Double.compare(expected, actual) != 0) {
            throw new AssertionError("Expected " + expected + ", got " + actual);
        }
    }

    private static void expect(Class<? extends Throwable> type, Runnable action) {
        try {
            action.run();
        } catch (Throwable error) {
            if (type.isInstance(error)) return;
            throw new AssertionError("Unexpected exception", error);
        }
        throw new AssertionError("Expected " + type.getSimpleName());
    }
}
