package com.flashsale.product.adapter.in.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterProductRequest(
    @NotBlank @Size(max = 200) String name,
    @NotNull @Min(0) Long price) {
}
