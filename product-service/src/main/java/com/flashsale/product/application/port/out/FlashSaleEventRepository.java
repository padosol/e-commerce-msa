package com.flashsale.product.application.port.out;

import com.flashsale.product.domain.FlashSaleEvent;
import java.util.Optional;

public interface FlashSaleEventRepository {

    FlashSaleEvent save(FlashSaleEvent event);

    Optional<FlashSaleEvent> findById(long id);
}
