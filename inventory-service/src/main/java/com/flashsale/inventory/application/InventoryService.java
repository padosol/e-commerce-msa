package com.flashsale.inventory.application;

import com.flashsale.inventory.application.port.out.BatchStockStore;
import com.flashsale.inventory.application.port.out.InventoryRepository;
import com.flashsale.inventory.application.port.out.StockStrategy;
import com.flashsale.inventory.domain.DecreaseResult;
import com.flashsale.inventory.domain.Inventory;
import com.flashsale.inventory.domain.InventoryNotFoundException;
import com.flashsale.inventory.domain.StockLine;
import java.util.HashSet;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class InventoryService {

    private final StockStrategy strategy;
    private final InventoryRepository repository;
    private final BatchStockStore batchStore;

    public InventoryService(StockStrategy strategy, InventoryRepository repository, BatchStockStore batchStore) {
        this.strategy = strategy;
        this.repository = repository;
        this.batchStore = batchStore;
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

    public DecreaseResult decreaseAll(List<StockLine> lines) {
        requireValid(lines);
        return batchStore.decreaseAll(lines);
    }

    public void increaseAll(List<StockLine> lines) {
        requireValid(lines);
        batchStore.increaseAll(lines);
    }

    private static void requireValid(List<StockLine> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("items must not be empty");
        }
        HashSet<Long> seen = new HashSet<>();
        for (StockLine line : lines) {
            if (!seen.add(line.productId())) {
                throw new IllegalArgumentException("duplicate productId " + line.productId());
            }
        }
    }

    public String strategyName() {
        return strategy.name();
    }
}
