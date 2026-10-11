package interview.question;

import java.util.Objects;

public record ShopCommodityPrice(Shop shop, Commodity commodity, Double price, Double min, Double max) {
    public ShopCommodityPrice {
        Objects.requireNonNull(shop, "shop must not be null");
        Objects.requireNonNull(commodity, "commodity must not be null");
        Objects.requireNonNull(price, "price must not be null");
    }
}
