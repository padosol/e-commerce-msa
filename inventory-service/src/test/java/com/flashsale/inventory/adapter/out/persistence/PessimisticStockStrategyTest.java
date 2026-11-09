package com.flashsale.inventory.adapter.out.persistence;

import com.flashsale.inventory.TestcontainersConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(properties = "inventory.strategy=pessimistic")
@Import(TestcontainersConfiguration.class)
class PessimisticStockStrategyTest extends StockStrategyContract {
}
