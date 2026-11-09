package com.flashsale.order.domain;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One user's cart: at most one line per product, in the order the products were first added. */
public class Cart {

    public static final int MIN_QUANTITY = 1;
    public static final int MAX_QUANTITY = 99;

    private final long userId;
    private final Map<Long, Integer> quantities = new LinkedHashMap<>();

    private Cart(long userId) {
        this.userId = userId;
    }

    public static Cart empty(long userId) {
        return new Cart(userId);
    }

    public static Cart restore(long userId, List<CartItem> items) {
        Cart cart = new Cart(userId);
        items.forEach(i -> cart.put(i.productId(), i.quantity()));
        return cart;
    }

    public void put(long productId, int quantity) {
        if (quantity < MIN_QUANTITY || quantity > MAX_QUANTITY) {
            throw new InvalidQuantityException(quantity);
        }
        quantities.put(productId, quantity);
    }

    public void remove(long productId) {
        quantities.remove(productId);
    }

    public void removeAll(Collection<Long> productIds) {
        productIds.forEach(quantities::remove);
    }

    public List<CartItem> items() {
        return quantities.entrySet().stream().map(e -> new CartItem(e.getKey(), e.getValue())).toList();
    }

    public boolean isEmpty() {
        return quantities.isEmpty();
    }

    public long userId() {
        return userId;
    }
}
