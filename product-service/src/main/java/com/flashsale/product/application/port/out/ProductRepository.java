package com.flashsale.product.application.port.out;

import com.flashsale.product.domain.Product;

public interface ProductRepository {

    Product save(Product product);
}
