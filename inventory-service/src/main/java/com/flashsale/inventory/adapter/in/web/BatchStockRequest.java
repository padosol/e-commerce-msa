package com.flashsale.inventory.adapter.in.web;

import com.flashsale.inventory.domain.StockLine;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record BatchStockRequest(@NotEmpty @Size(max = 100) List<@NotNull @Valid Item> items) {

    public record Item(@NotNull Long productId, @Min(1) int quantity) {
    }

    List<StockLine> toLines() {
        return items.stream().map(i -> new StockLine(i.productId(), i.quantity())).toList();
    }
}
