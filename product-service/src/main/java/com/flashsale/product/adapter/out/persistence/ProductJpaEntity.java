package com.flashsale.product.adapter.out.persistence;

import com.flashsale.product.domain.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "products")
public class ProductJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false)
    private long price;

    @Column(nullable = false)
    private boolean listed;

    protected ProductJpaEntity() {
    }

    static ProductJpaEntity from(Product product) {
        ProductJpaEntity entity = new ProductJpaEntity();
        entity.id = product.id();
        entity.name = product.name();
        entity.price = product.price();
        entity.listed = product.listed();
        return entity;
    }

    Product toDomain() {
        return Product.restore(id, name, price, listed);
    }
}
