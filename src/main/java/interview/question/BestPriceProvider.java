package interview.question;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class BestPriceProvider {
    private final ConcurrentHashMap<Commodity, ConcurrentHashMap<Shop, Double>> latestPrices = new ConcurrentHashMap<>();
    private final List<ShopCommodityPrice> priceRecords = Collections.synchronizedList(new ArrayList<>());

    /**
     * Stores the latest price reported by a shop for a commodity.
     *
     * @param shop reporting shop
     * @param commodity beverage being priced
     * @param price latest price from that shop
     */
    public synchronized void priceUpdate(@NotNull Shop shop, @NotNull Commodity commodity, double price) {
        Objects.requireNonNull(shop, "shop must not be null");
        Objects.requireNonNull(commodity, "commodity must not be null");
        validatePrice(price);

        latestPrices.computeIfAbsent(commodity, ignored -> new ConcurrentHashMap<>())
                .put(shop, price);
        refreshCommodityRecords(commodity);
    }

    /**
     * Returns the minimum currently reported price for a commodity.
     *
     * @param commodity beverage to query
     * @return lowest latest price across all shops that have reported this commodity
     */
    public synchronized double minPrice(@NotNull Commodity commodity) {
        Objects.requireNonNull(commodity, "commodity must not be null");
        return priceRecords.stream()
                .filter(record -> record.commodity() == commodity)
                .map(ShopCommodityPrice::min)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .min()
                .orElseThrow(() -> noPriceException(commodity));
    }

    /**
     * Returns the maximum currently reported price for a commodity.
     *
     * @param commodity beverage to query
     * @return highest latest price across all shops that have reported this commodity
     */
    public synchronized double maxPrice(@NotNull Commodity commodity) {
        Objects.requireNonNull(commodity, "commodity must not be null");
        return priceRecords.stream()
                .filter(record -> record.commodity() == commodity)
                .map(ShopCommodityPrice::max)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .max()
                .orElseThrow(() -> noPriceException(commodity));
    }

    /**
     * Returns all shops currently reporting the minimum price for a commodity.
     *
     * @param commodity beverage to query
     * @return shops tied for the lowest latest price
     */
    public synchronized List<Shop> minPriceShops(@NotNull Commodity commodity) {
        Objects.requireNonNull(commodity, "commodity must not be null");
        double min = minPrice(commodity);
        return priceRecords.stream()
                .filter(record -> record.commodity() == commodity)
                .filter(record -> record.min() != null && Double.compare(record.min(), min) == 0)
                .map(ShopCommodityPrice::shop)
                .sorted()
                .toList();
    }

    /**
     * Returns all shops currently reporting the maximum price for a commodity.
     *
     * @param commodity beverage to query
     * @return shops tied for the highest latest price
     */
    public synchronized List<Shop> maxPriceShops(@NotNull Commodity commodity) {
        Objects.requireNonNull(commodity, "commodity must not be null");
        double max = maxPrice(commodity);
        return priceRecords.stream()
                .filter(record -> record.commodity() == commodity)
                .filter(record -> record.max() != null && Double.compare(record.max(), max) == 0)
                .map(ShopCommodityPrice::shop)
                .sorted()
                .toList();
    }

    private static void validatePrice(double price) {
        if (!Double.isFinite(price)) {
            throw new IllegalArgumentException("price must be a finite number");
        }
        if (price < 0.0d) {
            throw new IllegalArgumentException("price must not be negative");
        }
    }

    private void refreshCommodityRecords(Commodity commodity) {
        ConcurrentHashMap<Shop, Double> commodityPrices = latestPrices.get(commodity);

        if (commodityPrices == null || commodityPrices.isEmpty()) {
            return;
        }

        double marketMin = Double.POSITIVE_INFINITY;
        double marketMax = Double.NEGATIVE_INFINITY;
        for (Double price : commodityPrices.values()) {
            marketMin = Math.min(marketMin, price);
            marketMax = Math.max(marketMax, price);
        }

        synchronized (priceRecords) {
            priceRecords.removeIf(record -> record.commodity() == commodity);
        }

        for (var entry : commodityPrices.entrySet()) {
            Double price = entry.getValue();
            Double min = Double.compare(price, marketMin) == 0 ? price : null;
            Double max = Double.compare(price, marketMax) == 0 ? price : null;
            synchronized (priceRecords) {
                priceRecords.add(new ShopCommodityPrice(entry.getKey(), commodity, price, min, max));
            }
        }
    }

    private static IllegalStateException noPriceException(Commodity commodity) {
        return new IllegalStateException("Waiting to receive a price for " + commodity);
    }

}
