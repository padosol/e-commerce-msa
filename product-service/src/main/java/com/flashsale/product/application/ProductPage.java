package com.flashsale.product.application;

import com.flashsale.product.domain.Product;
import java.util.List;

public record ProductPage(List<Product> products, int page, int size, long total) {
}
