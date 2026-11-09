package com.flashsale.product.application;

import com.flashsale.product.application.port.out.ProductRepository;
import com.flashsale.product.domain.Product;
import com.flashsale.product.domain.ProductNotFoundException;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

    private final ProductRepository products;

    public ProductService(ProductRepository products) {
        this.products = products;
    }

    @Transactional
    public Product register(String name, long price) {
        return products.save(Product.listed(name, price));
    }

    @Transactional(readOnly = true)
    public Product getListed(long productId) {
        return products.findListedById(productId).orElseThrow(() -> new ProductNotFoundException(productId));
    }

    @Transactional(readOnly = true)
    public ProductPage list(int page, int size) {
        return new ProductPage(products.findListed(page, size), page, size, products.countListed());
    }

    @Transactional(readOnly = true)
    public List<Product> findListed(Collection<Long> ids) {
        return products.findListedByIds(ids);
    }
}
