package interview.question;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.NoSuchElementException;


public class BestPriceProvider {

    // Built once in the constructor and never modified afterwards, so it is
    // safe to read from any thread without synchronization.
    private final EnumMap<Commodity, CommodityPrices> prices = new EnumMap<>(Commodity.class);

    public BestPriceProvider() {
        for (Commodity c : Commodity.values()) {
            prices.put(c, new CommodityPrices(Shop.values().length));
        }
    }

    public void priceUpdate(@NotNull Shop shop, @NotNull Commodity commodity, double price) {
        if (Double.isNaN(price) || Double.isInfinite(price) || price < 0) {
            throw new IllegalArgumentException("Invalid price: " + price);
        }
        prices.get(commodity).update(shop.ordinal(), price);
    }

    public double minPrice(@NotNull Commodity commodity) {
        return prices.get(commodity).range().min();
    }

    public double maxPrice(@NotNull Commodity commodity) {
        return prices.get(commodity).range().max();
    }

    /** Immutable min/max pair, so a reader always sees a consistent view. */
    private record Range(double min, double max) {}

    private static final class CommodityPrices {
        private static final Range EMPTY = new Range(Double.NaN, Double.NaN);

        private final double[] latest;            // guarded by "this"; NaN = never reported
        private volatile Range range = EMPTY;     // lock-free reads

        CommodityPrices(int shops) {
            latest = new double[shops];
            Arrays.fill(latest, Double.NaN);
        }

        synchronized void update(int shopIdx, double price) {
            latest[shopIdx] = price;

            // Full rescan over a tiny fixed array (4 shops) is cheaper and simpler
            // than tracking "was the old value the min/max?" logic.
            double lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY;
            for (double p : latest) {
                if (!Double.isNaN(p)) {
                    if (p < lo) lo = p;
                    if (p > hi) hi = p;
                }
            }
            // Only allocate and publish a new snapshot when something changed.
            Range cur = range;
            if (Double.compare(cur.min(), lo) != 0 || Double.compare(cur.max(), hi) != 0) {
                range = new Range(lo, hi);
            }
        }

        Range range() {
            Range r = range;
            if (Double.isNaN(r.min())) {
                throw new NoSuchElementException("No prices reported yet");
            }
            return r;
        }
    }
}