package com.flashsale.inventory.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class InventoryTest {

    @Test
    void negativeQuantityIsRejected() {
        assertThatThrownBy(() -> new Inventory(1, -1)).isInstanceOf(IllegalArgumentException.class);
    }
}
