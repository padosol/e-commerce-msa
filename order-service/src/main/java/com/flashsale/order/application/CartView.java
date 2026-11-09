package com.flashsale.order.application;

import java.util.List;

/** Lines whose product is no longer sold carry null name, price and subtotal and are left out of the total. */
public record CartView(List<Line> items, long total) {

    public record Line(long productId, String name, Long unitPrice, int quantity, Long subtotal, boolean available) {
    }
}
