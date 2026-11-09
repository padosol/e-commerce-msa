package com.flashsale.product.application.port.out;

import com.flashsale.product.domain.Product;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductRepository {

    Product save(Product product);

    Optional<Product> findListedById(long id);

    List<Product> findListed(int page, int size);

    long countListed();

    List<Product> findListedByIds(Collection<Long> ids);
}
