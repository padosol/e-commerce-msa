package com.flashsale.inventory.api;

import com.flashsale.inventory.domain.Inventory;
import com.flashsale.inventory.domain.InventoryRepository;
import com.flashsale.inventory.stock.InventoryNotFoundException;
import com.flashsale.inventory.stock.StockStats;
import com.flashsale.inventory.stock.StockStrategy;
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

    private final StockStrategy strategy;
    private final InventoryRepository repository;
    private final StockStats stats;

    public InventoryController(StockStrategy strategy, InventoryRepository repository, StockStats stats) {
        this.strategy = strategy;
        this.repository = repository;
        this.stats = stats;
    }

    @PutMapping("/inventory/{productId}")
    public InventoryResponse put(@PathVariable long productId, @Valid @RequestBody SetQuantityRequest request) {
        Inventory inventory = repository.findById(productId).orElseGet(() -> new Inventory(productId, 0));
        inventory.setQuantity(request.quantity());
        repository.save(inventory);
        return new InventoryResponse(productId, request.quantity());
    }

    @GetMapping("/inventory/{productId}")
    public InventoryResponse get(@PathVariable long productId) {
        Inventory inventory = repository.findById(productId)
            .orElseThrow(() -> new InventoryNotFoundException(productId));
        return new InventoryResponse(inventory.getProductId(), inventory.getQuantity());
    }

    @PostMapping("/inventory/{productId}/decrease")
    public DecreaseResponse decrease(@PathVariable long productId, @Valid @RequestBody QuantityRequest request) {
        return new DecreaseResponse(strategy.decrease(productId, request.quantity()));
    }

    @PostMapping("/inventory/{productId}/increase")
    public ResponseEntity<Void> increase(@PathVariable long productId, @Valid @RequestBody QuantityRequest request) {
        strategy.increase(productId, request.quantity());
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
        return new StrategyResponse(strategy.name());
    }
}
