package interview.question;

public class DemoApplication {
    public static void main(String[] args) {
        BestPriceProvider provider = new BestPriceProvider();

        provider.priceUpdate(Shop.TOAST_BOX, Commodity.COFFEE, 2.60);
        provider.priceUpdate(Shop.STARBUCKS, Commodity.COFFEE, 5.80);
        provider.priceUpdate(Shop.COFFEE_BEAN, Commodity.COFFEE, 5.40);
        provider.priceUpdate(Shop.KOMALA_VILAS, Commodity.TEA, 1.70);
        provider.priceUpdate(Shop.TOAST_BOX, Commodity.TEA, 1.90);
        provider.priceUpdate(Shop.STARBUCKS, Commodity.ICE_TEA, 4.80);
        provider.priceUpdate(Shop.COFFEE_BEAN, Commodity.ICE_TEA, 4.60);
        provider.priceUpdate(Shop.TOAST_BOX, Commodity.ICE_COFFEE, 3.40);
        provider.priceUpdate(Shop.STARBUCKS, Commodity.ICE_COFFEE, 3.40);
        provider.priceUpdate(Shop.COFFEE_BEAN, Commodity.ICE_COFFEE, 5.10);
        provider.priceUpdate(Shop.KOMALA_VILAS, Commodity.ICE_COFFEE, 5.10);

        System.out.println("Initial market:");
        printRange(provider, Commodity.COFFEE);
        printRange(provider, Commodity.TEA);
        printRange(provider, Commodity.ICE_TEA);
        printRange(provider, Commodity.ICE_COFFEE);

        provider.priceUpdate(Shop.STARBUCKS, Commodity.COFFEE, 2.40);
        System.out.println();
        System.out.println("After Starbucks updates coffee to 2.40:");
        printRange(provider, Commodity.COFFEE);

        provider.priceUpdate(Shop.TOAST_BOX, Commodity.ICE_COFFEE, 6.00);
        System.out.println();
        System.out.println("After Toast Box updates ice coffee from min to max:");
        printRange(provider, Commodity.ICE_COFFEE);
    }

    private static void printRange(BestPriceProvider provider, Commodity commodity) {
        System.out.printf("%-10s min: %.2f %s max: %.2f %s%n",
                commodity,
                provider.minPrice(commodity),
                provider.minPriceShops(commodity),
                provider.maxPrice(commodity),
                provider.maxPriceShops(commodity));
    }
}
