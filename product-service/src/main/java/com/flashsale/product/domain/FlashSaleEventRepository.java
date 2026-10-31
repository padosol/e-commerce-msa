package com.flashsale.product.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FlashSaleEventRepository extends JpaRepository<FlashSaleEvent, Long> {

    @EntityGraph(attributePaths = "product")
    Optional<FlashSaleEvent> findWithProductById(Long id);
}
