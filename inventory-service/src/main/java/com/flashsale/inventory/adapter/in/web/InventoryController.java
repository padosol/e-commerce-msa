package com.flashsale.inventory.adapter.in.web;

import com.flashsale.inventory.application.InventoryService;
import com.flashsale.inventory.application.StockStats;
import com.flashsale.inventory.domain.Inventory;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal")
public class InventoryController {

    private final InventoryService service;
    private final StockStats stats;

    public InventoryController(InventoryService service, StockStats stats) {
        this.service = service;
        this.stats = stats;
    }

    @PutMapping("/inventory/{productId}")
    public InventoryResponse put(@PathVariable long productId, @Valid @RequestBody SetQuantityRequest request) {
        Inventory inventory = service.set(productId, request.quantity());
        return new InventoryResponse(inventory.productId(), inventory.quantity());
    }

    @GetMapping("/inventory/{productId}")
    public InventoryResponse get(@PathVariable long productId) {
        Inventory inventory = service.get(productId);
        return new InventoryResponse(inventory.productId(), inventory.quantity());
    }

    @PostMapping("/inventory/{productId}/decrease")
    public DecreaseResponse decrease(@PathVariable long productId, @Valid @RequestBody QuantityRequest request) {
        return new DecreaseResponse(service.decrease(productId, request.quantity()));
    }

    @PostMapping("/inventory/{productId}/increase")
    public ResponseEntity<Void> increase(@PathVariable long productId, @Valid @RequestBody QuantityRequest request) {
        service.increase(productId, request.quantity());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/inventory/decrease-all")
    public DecreaseResponse decreaseAll(@Valid @RequestBody BatchStockRequest request) {
        return new DecreaseResponse(service.decreaseAll(request.toLines()));
    }

    @PostMapping("/inventory/increase-all")
    public ResponseEntity<Void> increaseAll(@Valid @RequestBody BatchStockRequest request) {
        service.increaseAll(request.toLines());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/inventory/stats")
    public StockStatsResponse stats() {
        StockStats.Snapshot snapshot = stats.snapshot();
        return new StockStatsResponse(snapshot.conflicts(), snapshot.retries(), snapshot.exhausted());
    }

    @DeleteMapping("/inventory/stats")
    public ResponseEntity<Void> resetStats() {
        stats.reset();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/strategy")
    public StrategyResponse strategy() {
        return new StrategyResponse(service.strategyName());
    }
}
