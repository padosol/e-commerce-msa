package com.flashsale.order.adapter.in.web;

import com.flashsale.order.application.CartView;
import java.util.List;

record CartResponse(List<Item> items, long total) {

    record Item(long productId, String name, Long unitPrice, int quantity, Long subtotal, boolean available) {
    }

    static CartResponse from(CartView view) {
        return new CartResponse(view.items().stream()
                .map(l -> new Item(l.productId(), l.name(), l.unitPrice(), l.quantity(), l.subtotal(), l.available()))
                .toList(), view.total());
    }
}
