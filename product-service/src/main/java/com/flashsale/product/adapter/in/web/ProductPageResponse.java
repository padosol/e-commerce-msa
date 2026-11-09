package com.flashsale.product.adapter.in.web;

import com.flashsale.product.application.ProductPage;
import java.util.List;

public record ProductPageResponse(List<ProductResponse> products, int page, int size, long total) {

    public static ProductPageResponse from(ProductPage p) {
        return new ProductPageResponse(
            p.products().stream().map(ProductResponse::from).toList(), p.page(), p.size(), p.total());
    }
}
