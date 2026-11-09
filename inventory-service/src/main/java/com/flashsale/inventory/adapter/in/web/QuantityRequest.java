package com.flashsale.inventory.adapter.in.web;

import jakarta.validation.constraints.Min;

public record QuantityRequest(@Min(1) int quantity) {
}
