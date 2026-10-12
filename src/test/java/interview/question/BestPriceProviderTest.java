package interview.question;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BestPriceProviderTest {

    @Test
    void returnsMinimumAndMaximumAcrossReportingShops() {
        BestPriceProvider provider = new BestPriceProvider();

        provider.priceUpdate(Shop.TOAST_BOX, Commodity.COFFEE, 2.50);
        provider.priceUpdate(Shop.STARBUCKS, Commodity.COFFEE, 5.75);
        provider.priceUpdate(Shop.COFFEE_BEAN, Commodity.COFFEE, 5.20);

        assertEquals(2.50, provider.minPrice(Commodity.COFFEE));
        assertEquals(5.75, provider.maxPrice(Commodity.COFFEE));
        assertEquals(Set.of(Shop.TOAST_BOX), provider.minPriceShops(Commodity.COFFEE));
        assertEquals(Set.of(Shop.STARBUCKS), provider.maxPriceShops(Commodity.COFFEE));
    }

    @Test
    void singleShopIsBothMinAndMax() {
        BestPriceProvider provider = new BestPriceProvider();

        provider.priceUpdate(Shop.KOMALA_VILAS, Commodity.TEA, 2.00);

        assertEquals(2.00, provider.minPrice(Commodity.TEA));
        assertEquals(2.00, provider.maxPrice(Commodity.TEA));
        assertEquals(Set.of(Shop.KOMALA_VILAS), provider.minPriceShops(Commodity.TEA));
        assertEquals(Set.of(Shop.KOMALA_VILAS), provider.maxPriceShops(Commodity.TEA));
    }

    @Test
    void replacesPreviousPriceFromSameShop() {
        BestPriceProvider provider = new BestPriceProvider();

        provider.priceUpdate(Shop.TOAST_BOX, Commodity.TEA, 1.60);
        provider.priceUpdate(Shop.STARBUCKS, Commodity.TEA, 4.20);
        provider.priceUpdate(Shop.STARBUCKS, Commodity.TEA, 1.50);

        assertEquals(1.50, provider.minPrice(Commodity.TEA));
        assertEquals(1.60, provider.maxPrice(Commodity.TEA));
        assertEquals(Set.of(Shop.STARBUCKS), provider.minPriceShops(Commodity.TEA));
        assertEquals(Set.of(Shop.TOAST_BOX), provider.maxPriceShops(Commodity.TEA));
    }

    @Test
    void oldMaxIsForgottenWhenThatShopLowersItsPrice() {
        BestPriceProvider provider = new BestPriceProvider();

        provider.priceUpdate(Shop.TOAST_BOX, Commodity.COFFEE, 2.00);
        provider.priceUpdate(Shop.STARBUCKS, Commodity.COFFEE, 9.00);
        provider.priceUpdate(Shop.STARBUCKS, Commodity.COFFEE, 3.00);

        assertEquals(3.00, provider.maxPrice(Commodity.COFFEE));
        assertEquals(Set.of(Shop.STARBUCKS), provider.maxPriceShops(Commodity.COFFEE));
    }

    @Test
    void tracksDuplicatePricesWhenOneShopChanges() {
        BestPriceProvider provider = new BestPriceProvider();

        provider.priceUpdate(Shop.TOAST_BOX, Commodity.ICE_COFFEE, 3.40);
        provider.priceUpdate(Shop.STARBUCKS, Commodity.ICE_COFFEE, 3.40);
        provider.priceUpdate(Shop.COFFEE_BEAN, Commodity.ICE_COFFEE, 5.10);
        provider.priceUpdate(Shop.TOAST_BOX, Commodity.ICE_COFFEE, 6.00);

        assertEquals(3.40, provider.minPrice(Commodity.ICE_COFFEE));
        assertEquals(6.00, provider.maxPrice(Commodity.ICE_COFFEE));
        assertEquals(Set.of(Shop.STARBUCKS), provider.minPriceShops(Commodity.ICE_COFFEE));
        assertEquals(Set.of(Shop.TOAST_BOX), provider.maxPriceShops(Commodity.ICE_COFFEE));
    }

    @Test
    void returnsAllShopsTiedForMinimumAndMaximum() {
        BestPriceProvider provider = new BestPriceProvider();

        provider.priceUpdate(Shop.TOAST_BOX, Commodity.ICE_TEA, 3.20);
        provider.priceUpdate(Shop.STARBUCKS, Commodity.ICE_TEA, 3.20);
        provider.priceUpdate(Shop.COFFEE_BEAN, Commodity.ICE_TEA, 4.70);
        provider.priceUpdate(Shop.KOMALA_VILAS, Commodity.ICE_TEA, 4.70);

        assertEquals(3.20, provider.minPrice(Commodity.ICE_TEA));
        assertEquals(4.70, provider.maxPrice(Commodity.ICE_TEA));
        assertEquals(Set.of(Shop.TOAST_BOX, Shop.STARBUCKS), provider.minPriceShops(Commodity.ICE_TEA));
        assertEquals(Set.of(Shop.COFFEE_BEAN, Shop.KOMALA_VILAS), provider.maxPriceShops(Commodity.ICE_TEA));
    }

    @Test
    void commoditiesAreIndependent() {
        BestPriceProvider provider = new BestPriceProvider();

        provider.priceUpdate(Shop.TOAST_BOX, Commodity.COFFEE, 2.00);
        provider.priceUpdate(Shop.TOAST_BOX, Commodity.TEA, 8.00);

        assertEquals(2.00, provider.minPrice(Commodity.COFFEE));
        assertEquals(8.00, provider.minPrice(Commodity.TEA));
        assertThrows(NoSuchElementException.class, () -> provider.minPrice(Commodity.ICE_TEA));
    }

    @Test
    void throwsWhenCommodityHasNoPricesYet() {
        BestPriceProvider provider = new BestPriceProvider();

        assertThrows(NoSuchElementException.class, () -> provider.minPrice(Commodity.ICE_TEA));
        assertThrows(NoSuchElementException.class, () -> provider.maxPrice(Commodity.ICE_TEA));
    }

    @Test
    void shopSetsAreEmptyWhenCommodityHasNoPricesYet() {
        BestPriceProvider provider = new BestPriceProvider();

        assertTrue(provider.minPriceShops(Commodity.ICE_TEA).isEmpty());
        assertTrue(provider.maxPriceShops(Commodity.ICE_TEA).isEmpty());
    }

    @Test
    void returnedShopSetsCannotBeModified() {
        BestPriceProvider provider = new BestPriceProvider();
        provider.priceUpdate(Shop.TOAST_BOX, Commodity.COFFEE, 2.00);

        assertThrows(UnsupportedOperationException.class,
                () -> provider.minPriceShops(Commodity.COFFEE).add(Shop.STARBUCKS));
    }

    @Test
    void rejectsInvalidPrices() {
        BestPriceProvider provider = new BestPriceProvider();

        assertThrows(IllegalArgumentException.class,
                () -> provider.priceUpdate(Shop.TOAST_BOX, Commodity.COFFEE, -1.00));
        assertThrows(IllegalArgumentException.class,
                () -> provider.priceUpdate(Shop.TOAST_BOX, Commodity.COFFEE, Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> provider.priceUpdate(Shop.TOAST_BOX, Commodity.COFFEE, Double.POSITIVE_INFINITY));
    }

    @Test
    void rejectedPriceDoesNotChangeState() {
        BestPriceProvider provider = new BestPriceProvider();
        provider.priceUpdate(Shop.TOAST_BOX, Commodity.COFFEE, 2.00);

        assertThrows(IllegalArgumentException.class,
                () -> provider.priceUpdate(Shop.TOAST_BOX, Commodity.COFFEE, -5.00));

        assertEquals(2.00, provider.minPrice(Commodity.COFFEE));
    }

    @Test
    void supportsConcurrentUpdatesAndQueries() throws Exception {
        BestPriceProvider provider = new BestPriceProvider();
        Shop[] shops = Shop.values();
        int updatesPerShop = 1_000;

        ExecutorService executor = Executors.newFixedThreadPool(shops.length);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        try {
            for (Shop shop : shops) {
                futures.add(executor.submit(() -> {
                    start.await();
                    for (int i = 0; i < updatesPerShop; i++) {
                        provider.priceUpdate(shop, Commodity.COFFEE, priceFor(shop, i));

                        // Each shop's price stays within [1 + ordinal, 2 + ordinal),
                        // so any snapshot must fall inside the global bounds.
                        double min = provider.minPrice(Commodity.COFFEE);
                        double max = provider.maxPrice(Commodity.COFFEE);
                        assertTrue(min >= 1.0 && min < shops.length + 1.0, "min out of bounds: " + min);
                        assertTrue(max >= 1.0 && max < shops.length + 1.0, "max out of bounds: " + max);
                        assertTrue(!provider.minPriceShops(Commodity.COFFEE).isEmpty());
                        assertTrue(!provider.maxPriceShops(Commodity.COFFEE).isEmpty());
                    }
                    return null;
                }));
            }

            start.countDown();

            // get() rethrows anything that failed inside a worker thread.
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        // Each shop's last write is deterministic, so the final answer is exact.
        // A stale-overwrite race (Option C style) would fail here.
        Shop cheapest = shops[0];
        Shop priciest = shops[shops.length - 1];
        assertEquals(priceFor(cheapest, updatesPerShop - 1), provider.minPrice(Commodity.COFFEE));
        assertEquals(priceFor(priciest, updatesPerShop - 1), provider.maxPrice(Commodity.COFFEE));
        assertEquals(Set.of(cheapest), provider.minPriceShops(Commodity.COFFEE));
        assertEquals(Set.of(priciest), provider.maxPriceShops(Commodity.COFFEE));
    }

    /** Same expression used by writers and assertions, so the doubles match exactly. */
    private static double priceFor(Shop shop, int i) {
        return 1.00 + shop.ordinal() + (i / 1000.0);
    }
}