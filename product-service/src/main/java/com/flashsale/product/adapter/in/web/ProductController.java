package com.flashsale.product.adapter.in.web;

import com.flashsale.product.application.ProductService;
import com.flashsale.product.domain.Product;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
public class ProductController {

    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    @GetMapping("/api/products")
    ProductPageResponse list(
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ProductPageResponse.from(service.list(page, size));
    }

    @GetMapping("/api/products/{productId}")
    ProductResponse get(@PathVariable long productId) {
        return ProductResponse.from(service.getListed(productId));
    }

    @PostMapping("/internal/products")
    @ResponseStatus(HttpStatus.CREATED)
    RegisterProductResponse register(@Valid @RequestBody RegisterProductRequest request) {
        Product product = service.register(request.name(), request.price());
        return new RegisterProductResponse(product.id());
    }

    @GetMapping("/internal/products")
    List<ProductResponse> findByIds(@RequestParam @Size(min = 1, max = 100) List<Long> ids) {
        return service.findListed(ids).stream().map(ProductResponse::from).toList();
    }
}
