# UOB Java challenge — BestPriceProvider

## Run (JDK 8 or newer)

From the folder containing both Java files:

```sh
javac -d out BestPriceProvider.java BestPriceProviderTest.java
java -cp out interview.question.BestPriceProviderTest
```

No external libraries are required. The starter's JetBrains `@NotNull` annotations
were removed to make compilation standalone; `Objects.requireNonNull` enforces
null checks at runtime. You may restore the annotations if your IDE project has
that dependency. The original package, enums, and public method signatures remain.

## Behaviour and assumptions

- Each (shop, commodity) stores its latest received quote, not historical quotes.
- Only shops that have reported a commodity participate in its min/max.
- A query with no reported quotes throws `NoSuchElementException`.
- Null arguments throw `NullPointerException`.
- Negative, NaN and infinite prices throw `IllegalArgumentException` without
  changing existing quotes. Zero is allowed; negative zero becomes positive zero.
- Prices have no expiration because the brief specifies no freshness policy.
- Concurrent updates for the same commodity take effect in write-lock order.
  This does not recover feed/event order: timestamps or sequence numbers would be
  needed to reject stale or out-of-order messages.

## Design and interview discussion

A fixed `EnumMap` holds one independent state per commodity. Each state uses a
read/write lock, arrays indexed by the shop enum, and cached min/max values.
Updating one commodity does not lock any other commodity. Equal repeated quotes
skip recomputation. Updating a previous minimum or maximum recomputes both extrema,
so stale historical prices never remain in query results. Tied quotes work naturally.

For S shops and C commodities:

- Update: O(S), scanning just four shops for the supplied enum.
- Min/max query: O(1), with a read lock.
- Storage: O(C × S), with no growing history or per-update object allocation.

Every individual operation is linearizable. Read locks protect quote visibility
and the cached extrema; readers cannot observe a partly applied update. Separate
calls to minPrice and maxPrice can observe different updates; add a single
snapshot method under one read lock if a caller needs an atomic pair.

The trade-off deliberately favours simple, bounded work and constant-time queries
for four shops. Read/write locking is not a promise of a particular throughput;
benchmark with representative feeds. The default non-fair lock can postpone
writers under contention. A fair lock improves scheduling fairness at a possible
throughput cost, while an immutable published extrema snapshot could eliminate
read-lock acquisition at the cost of update allocations.

Adding enum constants needs no algorithm changes. Dynamically registered shops
would call for maps rather than ordinal-indexed arrays. For thousands of shops,
a shop-to-price map plus a balanced price-to-count tree supports O(log S) updates
and correctly handles duplicate prices, with additional memory and complexity.
If Shop and Commodity must be referenced outside this package, move each enum
into its own public Java file (their package-private visibility matches the starter).

The brief mandates doubles; real monetary systems should usually choose integer
minor units or BigDecimal plus an explicit currency and rounding policy instead.

## Tests

The standalone suite checks empty queries, quote replacement, ties, identical
updates, independent commodities, validation, zero prices, and simultaneous writers
and readers. Concurrent tests also verify exact final extrema after all writers
finish. They are regression checks, not a formal proof of linearizability or a
throughput benchmark.
