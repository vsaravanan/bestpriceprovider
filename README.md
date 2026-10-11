# Best Price Provider

Runnable Java solution for the `BestPriceProvider` interview challenge.

The application receives live beverage price updates from multiple shops and answers:

- the current minimum price for a commodity
- the current maximum price for a commodity

Each shop has at most one current price per commodity. A later update from the same shop replaces that shop's previous price for that commodity.

## Project Layout

```text
src/main/java/interview/question/
  BestPriceProvider.java   Thread-safe provider implementation
  Commodity.java           Beverage enum
  Shop.java                Shop enum
  DemoApplication.java     Simple runnable demo

src/test/java/interview/question/
  BestPriceProviderTest.java
```

## Build and Test

```bash
mvn test
```

## Run the Demo

```bash
mvn exec:java
```

Expected output is similar to:

```text
Initial market:
COFFEE     min: 2.60 [TOAST_BOX] max: 5.80 [STARBUCKS]
TEA        min: 1.70 [KOMALA_VILAS] max: 1.90 [TOAST_BOX]
ICE_TEA    min: 4.60 [COFFEE_BEAN] max: 4.80 [STARBUCKS]
ICE_COFFEE min: 3.40 [TOAST_BOX, STARBUCKS] max: 5.10 [COFFEE_BEAN, KOMALA_VILAS]

After Starbucks updates coffee to 2.40:
COFFEE     min: 2.40 [STARBUCKS] max: 5.40 [COFFEE_BEAN]

After Toast Box updates ice coffee from min to max:
ICE_COFFEE min: 3.40 [STARBUCKS] max: 6.00 [TOAST_BOX]
```

## Design Notes

`BestPriceProvider` keeps a simple list of `ShopCommodityPrice` records:

```java
record ShopCommodityPrice(Shop shop, Commodity commodity, Double price, Double min, Double max)
```

For this challenge, each shop reports one latest price for each commodity. The provider stores the latest raw price in nested `ConcurrentHashMap`s:

```java
commodity -> shop -> latest price
```

After each update, it refreshes the records only for the affected commodity:

- `price` is always the shop's latest reported price
- `min` is populated only for shops currently selling at the market minimum
- `max` is populated only for shops currently selling at the market maximum
- non-winning `min` or `max` values are `null`

If multiple shops have the same lowest price, they all have `min` populated. The same logic applies to `max`.

Queries scan the record list for the requested commodity:

- `minPrice` returns the populated market minimum
- `maxPrice` returns the populated market maximum
- `minPriceShops` returns all shops tied at the market minimum
- `maxPriceShops` returns all shops tied at the market maximum

This is easy to explain in an interview and works well because the number of shops and commodities is small.

## Concurrency and Trade-offs

- Thread safety is handled with `synchronized` public methods.
- The nested `ConcurrentHashMap` stores the latest raw updates by commodity and shop.
- `synchronized` keeps the derived record list consistent while min/max markers are refreshed.
- Updates and queries are serialized, which is acceptable for this small challenge.
- The challenge uses `double` prices because that is in the starter signature. In a production payment system, storing prices as integer cents or `BigDecimal` would avoid floating-point representation issues.

The trade-off is that each update refreshes records for that commodity, and queries scan the list. This avoids scanning unrelated commodities and avoids temporary stream/list allocations. It is fine for four shops. If the number of shops became very large or queries were extremely frequent, a sorted index such as a `TreeMap` could make min/max lookups faster, but it would add more bookkeeping and locking complexity.
