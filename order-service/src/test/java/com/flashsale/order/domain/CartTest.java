package com.flashsale.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class CartTest {

    @Test
    void putAddsThenReplacesQuantity() {
        Cart cart = Cart.empty(1);
        cart.put(7, 2);
        cart.put(7, 5);

        assertThat(cart.items()).containsExactly(new CartItem(7, 5));
    }

    @Test
    void quantityOutside1To99IsRejected() {
        Cart cart = Cart.empty(1);

        assertThatThrownBy(() -> cart.put(7, 0)).isInstanceOf(InvalidQuantityException.class);
        assertThatThrownBy(() -> cart.put(7, 100)).isInstanceOf(InvalidQuantityException.class);
        assertThat(cart.isEmpty()).isTrue();
    }

    @Test
    void removeMissingIsNoop() {
        Cart cart = Cart.restore(1, List.of(new CartItem(7, 1)));
        cart.remove(8);

        assertThat(cart.items()).containsExactly(new CartItem(7, 1));
    }

    @Test
    void removeAllRemovesOnlyGiven() {
        Cart cart = Cart.restore(1, List.of(new CartItem(7, 1), new CartItem(8, 1), new CartItem(9, 1)));
        cart.removeAll(List.of(7L, 9L));

        assertThat(cart.items()).containsExactly(new CartItem(8, 1));
    }
}
