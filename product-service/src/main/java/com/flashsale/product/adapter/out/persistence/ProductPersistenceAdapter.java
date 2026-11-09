package com.flashsale.product.adapter.out.persistence;

import com.flashsale.product.application.port.out.ProductRepository;
import com.flashsale.product.domain.Product;
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
}
