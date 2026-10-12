package interview.question;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe service that receives live price updates from multiple coffee-shop feeds
 * and answers min / max price queries for a commodity across all reporting shops.
 *
 * Design goals addressed:
 *  - Concurrency & Thread-Safety : ConcurrentHashMap + per-commodity ReadWriteLock
 *  - High-Frequency Updates      : O(1) update path, minimal locking contention
 *  - Extensibility               : Easy to add new shops / commodities (EnumMap) or
 *                                  change aggregation strategy without touching callers
 */
public class BestPriceProvider {

    /**
     * Holds the latest known price for a single (shop, commodity) pair.
     * Using Double object so we can distinguish "never seen" (null) from a real price.
     */
    private final ConcurrentHashMap<Shop, ConcurrentHashMap<Commodity, Double>> prices =
            new ConcurrentHashMap<>();

    /**
     * Per-commodity caches of the current min and max so that queries stay O(1).
     * Protected by a ReadWriteLock so that concurrent readers never block each other
     * while a single writer can update both the map and the caches atomically.
     */
    private final Map<Commodity, Double> minCache = new EnumMap<>(Commodity.class);
    private final Map<Commodity, Double> maxCache = new EnumMap<>(Commodity.class);
    private final ReentrantReadWriteLock cacheLock = new ReentrantReadWriteLock();

    public BestPriceProvider() {
        // Pre-populate the outer map so that the first update for any shop is cheap.
        for (Shop shop : Shop.values()) {
            prices.put(shop, new ConcurrentHashMap<>());
        }
    }

    /**
     * Called by various Shop price feeds to send their latest price for a commodity.
     * Thread-safe and designed for high-frequency invocation.
     */
    public void priceUpdate(Shop shop, Commodity commodity, double price) {
        if (Double.isNaN(price) || price < 0) {
            // Reject obviously invalid prices; real systems may log or throw.
            throw new IllegalArgumentException("Price must be a non-negative number: " + price);
        }

        ConcurrentHashMap<Commodity, Double> shopPrices = prices.get(shop);
        // put returns the previous value (or null)
        Double previous = shopPrices.put(commodity, price);

        // Only recompute min/max when the value actually changed.
        if (previous == null || Double.compare(previous, price) != 0) {
            recomputeMinMax(commodity);
        }
    }

    /**
     * Returns the minimum price provided by any of the Shops for a commodity.
     * Returns Double.POSITIVE_INFINITY when no price has been reported yet.
     */
    public double minPrice(Commodity commodity) {
        cacheLock.readLock().lock();
        try {
            Double min = minCache.get(commodity);
            return min != null ? min : Double.POSITIVE_INFINITY;
        } finally {
            cacheLock.readLock().unlock();
        }
    }

    /**
     * Returns the maximum price provided by any of the Shops for a commodity.
     * Returns Double.NEGATIVE_INFINITY when no price has been reported yet.
     */
    public double maxPrice(Commodity commodity) {
        cacheLock.readLock().lock();
        try {
            Double max = maxCache.get(commodity);
            return max != null ? max : Double.NEGATIVE_INFINITY;
        } finally {
            cacheLock.readLock().unlock();
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Recomputes the global min and max for the given commodity by scanning the
     * latest prices of every shop.  Because the number of shops is tiny (and fixed),
     * a full scan is both simple and fast enough for the expected load.
     */
    private void recomputeMinMax(Commodity commodity) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        boolean found = false;

        for (ConcurrentHashMap<Commodity, Double> shopPrices : prices.values()) {
            Double p = shopPrices.get(commodity);
            if (p != null) {
                found = true;
                if (p < min) min = p;
                if (p > max) max = p;
            }
        }

        cacheLock.writeLock().lock();
        try {
            if (found) {
                minCache.put(commodity, min);
                maxCache.put(commodity, max);
            } else {
                // No prices left (e.g. after a future "clear" operation)
                minCache.remove(commodity);
                maxCache.remove(commodity);
            }
        } finally {
            cacheLock.writeLock().unlock();
        }
    }

    // -------------------------------------------------------------------------
    // Optional diagnostic helpers (useful during interview discussion / testing)
    // -------------------------------------------------------------------------

    /** Returns the latest price reported by a specific shop, or null if never reported. */
    public Double getPrice(Shop shop, Commodity commodity) {
        return prices.get(shop).get(commodity);
    }

    /** Returns a defensive snapshot of all known prices (shop -> commodity -> price). */
    public Map<Shop, Map<Commodity, Double>> snapshot() {
        Map<Shop, Map<Commodity, Double>> result = new EnumMap<>(Shop.class);
        for (Map.Entry<Shop, ConcurrentHashMap<Commodity, Double>> e : prices.entrySet()) {
            result.put(e.getKey(), new EnumMap<>(e.getValue()));
        }
        return result;
    }
}

// ---------------------------------------------------------------------------
// Supporting enums (kept in the same file for the interview exercise)
// ---------------------------------------------------------------------------

enum Shop {
    TOAST_BOX, STARBUCKS, COFFEE_BEAN, KOMALA_VILAS
}

enum Commodity {
    COFFEE, TEA, ICE_TEA, ICE_COFFEE
}
