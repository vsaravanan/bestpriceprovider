# Best Price Provider

Runnable Java solution for the `BestPriceProvider` interview challenge.

The application receives live beverage price updates from multiple shops and answers:

- the current minimum price for a commodity, and the shops currently quoting it
- the current maximum price for a commodity, and the shops currently quoting it

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

`BestPriceProvider` keeps one `CommodityPrices` object per commodity. It stores the latest price from each shop in a `double[]` indexed by `Shop.ordinal()`, where `NaN` means the shop has not reported yet.

On every update, the provider:

1. validates the price (non-negative, not `NaN`, not infinite)
2. stores it in the reporting shop's slot, replacing that shop's previous price
3. rescans the (small, fixed) array to find the min, the max and every shop tied at each
4. publishes the result as an immutable `Range` snapshot

Queries only read the published snapshot:

- `minPrice` returns the market minimum
- `maxPrice` returns the market maximum
- `minPriceShops` returns all shops tied at the market minimum
- `maxPriceShops` returns all shops tied at the market maximum

A full rescan is easy to explain and correct for any kind of change, including a shop raising its price from the old min or lowering it from the old max. It is cheap because there are only four shops.

## Concurrency and Trade-offs

### Design

- Each `Commodity` has its own `CommodityPrices` object, held in an `EnumMap` that is built in the constructor and never modified afterwards.
- `CommodityPrices` keeps the latest price per shop in a `double[]` indexed by `Shop.ordinal()`. `NaN` means the shop has not reported yet.
- Every update recomputes the min and max, and the shops that hold them, with a full scan of that array. With four shops this is cheaper and simpler than tracking whether the replaced value was the old min or max, and it handles price increases and decreases the same way.
- The result is stored in an immutable `Range` record (min, minShops, max, maxShops) and published through a `volatile` field.

### Thread safety

- **Writers:** `update` is `synchronized` per commodity. The price write and the min/max recompute happen in one critical section, so a stale result can never overwrite a newer one. Updates to different commodities do not contend.
- **Readers:** `minPrice`, `maxPrice`, `minPriceShops` and `maxPriceShops` take no lock. They read one volatile reference, so they always see a consistent snapshot, and a price is never paired with the wrong shops.
- **Sharing:** the returned shop sets are unmodifiable and never changed after publication, so they are returned without copying.

### High-frequency updates

- Prices are stored as primitives, with no boxing and no hash lookups.
- A new `Range` is published only when the min, max or tied shops actually change. Most ticks leave the reference as it was.
- Queries are O(1) and lock-free. Updates are O(number of shops).

### Behaviour notes

- `minPrice` and `maxPrice` throw `NoSuchElementException` if the commodity has no prices yet. `minPriceShops` and `maxPriceShops` return an empty set.
- Ties are returned in full: every shop quoting the min or max price is included.
- Invalid prices (negative, `NaN`, infinite) throw `IllegalArgumentException` and leave state unchanged.
- `minPrice` and `maxPrice` are separate calls, so two consecutive reads may come from different snapshots. A caller that needs both from one snapshot would need a method that returns the `Range`.

### Trade-offs and extensions

- The challenge uses `double` because it is in the starter signature. In a production system, integer cents or `BigDecimal` would avoid floating-point issues, and tie detection with `==` would then be exact.
- A shop that stops reporting keeps its last price forever. A per-shop timestamp with a TTL would fix this.
- Adding a `Shop` or `Commodity` needs no code changes, because the arrays and the `EnumMap` are sized from `values()`.
- If one commodity received extreme update rates, the per-commodity lock could become a bottleneck. Options are a lock-free design with CAS on the latest prices, or a single-writer queue. For four shops this would add complexity without a measurable benefit.
- If the number of shops grew very large, a full scan per update would become expensive. A sorted structure such as a `TreeMap` of price to shops would give O(log n) updates and min/max lookups, at the cost of more bookkeeping.