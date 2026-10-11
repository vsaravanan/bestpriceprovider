package interview.question;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
        assertEquals(List.of(Shop.TOAST_BOX), provider.minPriceShops(Commodity.COFFEE));
        assertEquals(List.of(Shop.STARBUCKS), provider.maxPriceShops(Commodity.COFFEE));
    }

    @Test
    void replacesPreviousPriceFromSameShop() {
        BestPriceProvider provider = new BestPriceProvider();

        provider.priceUpdate(Shop.TOAST_BOX, Commodity.TEA, 1.60);
        provider.priceUpdate(Shop.STARBUCKS, Commodity.TEA, 4.20);
        provider.priceUpdate(Shop.STARBUCKS, Commodity.TEA, 1.50);

        assertEquals(1.50, provider.minPrice(Commodity.TEA));
        assertEquals(1.60, provider.maxPrice(Commodity.TEA));
        assertEquals(List.of(Shop.STARBUCKS), provider.minPriceShops(Commodity.TEA));
        assertEquals(List.of(Shop.TOAST_BOX), provider.maxPriceShops(Commodity.TEA));
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
        assertEquals(List.of(Shop.STARBUCKS), provider.minPriceShops(Commodity.ICE_COFFEE));
        assertEquals(List.of(Shop.TOAST_BOX), provider.maxPriceShops(Commodity.ICE_COFFEE));
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
        assertEquals(List.of(Shop.TOAST_BOX, Shop.STARBUCKS), provider.minPriceShops(Commodity.ICE_TEA));
        assertEquals(List.of(Shop.COFFEE_BEAN, Shop.KOMALA_VILAS), provider.maxPriceShops(Commodity.ICE_TEA));
    }

    @Test
    void throwsWhenCommodityHasNoPricesYet() {
        BestPriceProvider provider = new BestPriceProvider();

        assertThrows(IllegalStateException.class, () -> provider.minPrice(Commodity.ICE_TEA));
        assertThrows(IllegalStateException.class, () -> provider.maxPrice(Commodity.ICE_TEA));
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
    void supportsConcurrentUpdatesAndQueries() throws Exception {
        BestPriceProvider provider = new BestPriceProvider();
        int workerCount = Shop.values().length;
        ExecutorService executor = Executors.newFixedThreadPool(workerCount);
        CountDownLatch start = new CountDownLatch(1);

        for (Shop shop : Shop.values()) {
            executor.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < 1_000; i++) {
                        double price = 1.00 + shop.ordinal() + (i / 1000.0);
                        provider.priceUpdate(shop, Commodity.COFFEE, price);
                        assertDoesNotThrow(() -> provider.minPrice(Commodity.COFFEE));
                        assertDoesNotThrow(() -> provider.maxPrice(Commodity.COFFEE));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        start.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        assertTrue(provider.minPrice(Commodity.COFFEE) <= provider.maxPrice(Commodity.COFFEE));
    }
}
