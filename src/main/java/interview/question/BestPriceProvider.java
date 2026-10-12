package interview.question;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.NoSuchElementException;
import java.util.Set;

public class BestPriceProvider {

    private static final Shop[] SHOPS = Shop.values();

    private final EnumMap<Commodity, CommodityPrices> prices = new EnumMap<>(Commodity.class);

    public BestPriceProvider() {
        for (Commodity c : Commodity.values()) {
            prices.put(c, new CommodityPrices(SHOPS.length));
        }
    }

    public void priceUpdate(@NotNull Shop shop, @NotNull Commodity commodity, double price) {
        if (Double.isNaN(price) || Double.isInfinite(price) || price < 0) {
            throw new IllegalArgumentException("Invalid price: " + price);
        }
        prices.get(commodity).update(shop.ordinal(), price);
    }

    public double minPrice(@NotNull Commodity commodity) {
        return prices.get(commodity).requireRange().min();
    }

    public double maxPrice(@NotNull Commodity commodity) {
        return prices.get(commodity).requireRange().max();
    }

    /** Shops quoting the lowest price. Empty if no data yet. */
    public Set<Shop> minPriceShops(@NotNull Commodity commodity) {
        return prices.get(commodity).range().minShops();
    }

    /** Shops quoting the highest price. Empty if no data yet. */
    public Set<Shop> maxPriceShops(@NotNull Commodity commodity) {
        return prices.get(commodity).range().maxShops();
    }

    /** Immutable snapshot of the current min and max. */
    private record Range(double min, Set<Shop> minShops, double max, Set<Shop> maxShops) {
        boolean isEmpty() {
            return Double.isNaN(min);
        }
    }

    private static final class CommodityPrices {
        private static final Range EMPTY =
                new Range(Double.NaN, Set.of(), Double.NaN, Set.of());

        private final double[] latest;          // guarded by "this"; NaN = never reported
        private volatile Range range = EMPTY;   // readers use this without a lock

        CommodityPrices(int shopCount) {
            latest = new double[shopCount];
            Arrays.fill(latest, Double.NaN);
        }

        synchronized void update(int shopIndex, double price) {
            latest[shopIndex] = price;

            Range updated = computeRange();
            if (!updated.equals(range)) {
                range = updated;
            }
        }

        Range range() {
            return range;
        }

        /** Returns the current snapshot, or throws if no shop has reported yet. */
        Range requireRange() {
            Range current = range;
            if (current.isEmpty()) {
                throw new NoSuchElementException("No prices reported yet");
            }
            return current;
        }

        private Range computeRange() {
            double lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY;
            EnumSet<Shop> loShops = EnumSet.noneOf(Shop.class);
            EnumSet<Shop> hiShops = EnumSet.noneOf(Shop.class);

            for (int i = 0; i < latest.length; i++) {
                double p = latest[i];
                if (Double.isNaN(p)) continue;

                if (p < lo)       { lo = p; loShops.clear(); loShops.add(SHOPS[i]); }
                else if (p == lo) { loShops.add(SHOPS[i]); }

                if (p > hi)       { hi = p; hiShops.clear(); hiShops.add(SHOPS[i]); }
                else if (p == hi) { hiShops.add(SHOPS[i]); }
            }

            return new Range(lo, Collections.unmodifiableSet(loShops),
                    hi, Collections.unmodifiableSet(hiShops));
        }
    }
}