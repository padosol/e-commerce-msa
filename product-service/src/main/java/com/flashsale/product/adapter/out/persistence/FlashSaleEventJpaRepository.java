package com.flashsale.product.adapter.out.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

interface FlashSaleEventJpaRepository extends JpaRepository<FlashSaleEventJpaEntity, Long> {

    @EntityGraph(attributePaths = "product")
    Optional<FlashSaleEventJpaEntity> findWithProductById(Long id);
}
