package com.flashsale.product.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record CreateEventRequest(
    @NotBlank @Size(max = 200) String productName,
    @NotNull @Min(0) Long price,
    @NotNull Instant openAt,
    @NotNull @Min(1) Integer saleQuantity) {
}
