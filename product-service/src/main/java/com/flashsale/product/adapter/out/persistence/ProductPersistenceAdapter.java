package com.flashsale.product.adapter.out.persistence;

import com.flashsale.product.application.port.out.ProductRepository;
import com.flashsale.product.domain.Product;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
class ProductPersistenceAdapter implements ProductRepository {

    private final ProductJpaRepository jpa;

    ProductPersistenceAdapter(ProductJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Product save(Product product) {
        return jpa.save(ProductJpaEntity.from(product)).toDomain();
    }

    @Override
    public Optional<Product> findListedById(long id) {
        return jpa.findByIdAndListedTrue(id).map(ProductJpaEntity::toDomain);
    }

    @Override
    public List<Product> findListed(int page, int size) {
        return jpa.findByListedTrueOrderByIdAsc(PageRequest.of(page, size)).stream()
            .map(ProductJpaEntity::toDomain).toList();
    }

    @Override
    public long countListed() {
        return jpa.countByListedTrue();
    }

    @Override
    public List<Product> findListedByIds(Collection<Long> ids) {
        return jpa.findByListedTrueAndIdInOrderByIdAsc(ids).stream()
            .map(ProductJpaEntity::toDomain).toList();
    }
}
