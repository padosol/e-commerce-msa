package com.flashsale.order.adapter.in.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

record PutCartItemRequest(@Min(1) @Max(99) int quantity) {
}
