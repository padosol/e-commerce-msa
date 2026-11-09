package com.flashsale.product.adapter.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface ProductJpaRepository extends JpaRepository<ProductJpaEntity, Long> {

    Optional<ProductJpaEntity> findByIdAndListedTrue(Long id);

    List<ProductJpaEntity> findByListedTrueOrderByIdAsc(Pageable pageable);

    long countByListedTrue();

    List<ProductJpaEntity> findByListedTrueAndIdInOrderByIdAsc(Collection<Long> ids);
}
