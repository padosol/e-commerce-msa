package com.flashsale.order.application.port.out;

import com.flashsale.order.domain.Cart;

public interface CartRepository {

    /** A user without any saved lines gets an empty cart. */
    Cart findByUserId(long userId);

    /** Replaces all of the user's lines in one transaction. */
    void save(Cart cart);
}
