package com.flashsale.product.adapter.out.persistence;

import com.flashsale.product.application.port.out.FlashSaleEventRepository;
import com.flashsale.product.domain.FlashSaleEvent;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
class FlashSaleEventPersistenceAdapter implements FlashSaleEventRepository {

    private final FlashSaleEventJpaRepository jpa;

    FlashSaleEventPersistenceAdapter(FlashSaleEventJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public FlashSaleEvent save(FlashSaleEvent event) {
        return jpa.save(FlashSaleEventJpaEntity.from(event)).toDomain();
    }

    @Override
    public Optional<FlashSaleEvent> findById(long id) {
        return jpa.findWithProductById(id).map(FlashSaleEventJpaEntity::toDomain);
    }
}
