package com.flashsale.inventory.api;

import jakarta.validation.constraints.Min;

public record SetQuantityRequest(@Min(0) int quantity) {
}
