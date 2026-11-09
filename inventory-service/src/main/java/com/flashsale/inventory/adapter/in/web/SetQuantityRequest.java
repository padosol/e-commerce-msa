package com.flashsale.inventory.adapter.in.web;

import jakarta.validation.constraints.Min;

public record SetQuantityRequest(@Min(0) int quantity) {
}
