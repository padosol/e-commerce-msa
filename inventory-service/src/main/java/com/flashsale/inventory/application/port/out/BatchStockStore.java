package com.flashsale.inventory.application.port.out;

import com.flashsale.inventory.domain.DecreaseResult;
import com.flashsale.inventory.domain.StockLine;
import java.util.List;

public interface BatchStockStore {

    /** 모든 줄을 차감하거나 하나도 차감하지 않는다. SUCCESS 또는 OUT_OF_STOCK 만 돌려준다. */
    DecreaseResult decreaseAll(List<StockLine> lines);

    /** 모든 줄을 복구한다. 재고 행이 없는 상품이 있으면 전부 되돌리고 InventoryNotFoundException 을 던진다. */
    void increaseAll(List<StockLine> lines);
}
