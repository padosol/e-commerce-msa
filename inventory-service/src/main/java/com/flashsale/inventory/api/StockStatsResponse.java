package com.flashsale.inventory.api;

public record StockStatsResponse(long conflicts, long retries, long exhausted) {
}
