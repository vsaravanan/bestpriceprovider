package interview.question;

import java.util.EnumMap;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantReadWriteLock;

enum Shop {
    TOAST_BOX, STARBUCKS, COFFEE_BEAN, KOMALA_VILAS;
}

enum Commodity {
    COFFEE, TEA, ICE_TEA, ICE_COFFEE;
}

/** Stores each shop's latest quote, independently for each commodity. */
public class BestPriceProvider {
    // Built once in the constructor, then never structurally modified.
    private final EnumMap<Commodity, CommodityPrices> prices =
            new EnumMap<>(Commodity.class);

    public BestPriceProvider() {
        for (Commodity commodity : Commodity.values()) {
            prices.put(commodity, new CommodityPrices());
        }
    }

    /**
     * Replaces this shop's previous quote. Concurrent calls for a commodity
     * are ordered by acquisition of its write lock, not feed timestamps.
     *
     * @throws NullPointerException if shop or commodity is null
     * @throws IllegalArgumentException if price is negative or non-finite
     */
    public void priceUpdate(Shop shop, Commodity commodity, double price) {
        Objects.requireNonNull(shop, "shop");
        Objects.requireNonNull(commodity, "commodity");
        if (!Double.isFinite(price) || price < 0.0) {
            throw new IllegalArgumentException("price must be finite and non-negative");
        }
        // Treat -0.0 and +0.0 as the same monetary value.
        prices.get(commodity).update(shop, price == 0.0 ? 0.0 : price);
    }

    /** @throws NoSuchElementException if no shop has reported this commodity */
    public double minPrice(Commodity commodity) {
        return prices.get(Objects.requireNonNull(commodity, "commodity")).extreme(true);
    }

    /** @throws NoSuchElementException if no shop has reported this commodity */
    public double maxPrice(Commodity commodity) {
        return prices.get(Objects.requireNonNull(commodity, "commodity")).extreme(false);
    }

    private static final class CommodityPrices {
        private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        private final double[] latest = new double[Shop.values().length];
        private final boolean[] reported = new boolean[latest.length];
        private boolean hasQuotes;
        private double min;
        private double max;

        void update(Shop shop, double price) {
            lock.writeLock().lock();
            try {
                int index = shop.ordinal();
                if (reported[index] && latest[index] == price) {
                    return;
                }
                latest[index] = price;
                reported[index] = true;
                double nextMin = Double.POSITIVE_INFINITY;
                double nextMax = Double.NEGATIVE_INFINITY;
                for (int i = 0; i < latest.length; i++) {
                    if (reported[i]) {
                        nextMin = Math.min(nextMin, latest[i]);
                        nextMax = Math.max(nextMax, latest[i]);
                    }
                }
                min = nextMin;
                max = nextMax;
                hasQuotes = true;
            } finally {
                lock.writeLock().unlock();
            }
        }

        double extreme(boolean minimum) {
            lock.readLock().lock();
            try {
                if (!hasQuotes) {
                    throw new NoSuchElementException("No prices reported for this commodity");
                }
                return minimum ? min : max;
            } finally {
                lock.readLock().unlock();
            }
        }
    }
}
