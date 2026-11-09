package com.flashsale.inventory.application;

import com.flashsale.inventory.application.port.out.InventoryRepository;
import com.flashsale.inventory.application.port.out.StockStrategy;
import com.flashsale.inventory.domain.DecreaseResult;
import com.flashsale.inventory.domain.Inventory;
import com.flashsale.inventory.domain.InventoryNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class InventoryService {

    private final StockStrategy strategy;
    private final InventoryRepository repository;

    public InventoryService(StockStrategy strategy, InventoryRepository repository) {
        this.strategy = strategy;
        this.repository = repository;
    }

    public Inventory set(long productId, int quantity) {
        Inventory inventory = new Inventory(productId, quantity);
        repository.save(inventory);
        return inventory;
    }

    public Inventory get(long productId) {
        return repository.findById(productId).orElseThrow(() -> new InventoryNotFoundException(productId));
    }

    public DecreaseResult decrease(long productId, int quantity) {
        return strategy.decrease(productId, quantity);
    }

    public void increase(long productId, int quantity) {
        strategy.increase(productId, quantity);
    }

    public String strategyName() {
        return strategy.name();
    }
}
