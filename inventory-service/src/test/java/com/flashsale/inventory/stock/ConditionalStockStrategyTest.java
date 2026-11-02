package com.flashsale.inventory.stock;

import com.flashsale.inventory.TestcontainersConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(properties = "inventory.strategy=conditional")
@Import(TestcontainersConfiguration.class)
class ConditionalStockStrategyTest extends StockStrategyContract {
}
