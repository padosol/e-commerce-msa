package com.flashsale.inventory.adapter.in.web;

public record StockStatsResponse(long conflicts, long retries, long exhausted) {
}
