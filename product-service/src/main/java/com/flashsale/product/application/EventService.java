package com.flashsale.product.application;

import com.flashsale.product.application.port.out.FlashSaleEventRepository;
import com.flashsale.product.application.port.out.ProductRepository;
import com.flashsale.product.domain.EventNotFoundException;
import com.flashsale.product.domain.FlashSaleEvent;
import com.flashsale.product.domain.Product;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventService {

    private final ProductRepository products;
    private final FlashSaleEventRepository events;

    public EventService(ProductRepository products, FlashSaleEventRepository events) {
        this.products = products;
        this.events = events;
    }

    @Transactional
    public FlashSaleEvent create(String productName, long price, Instant openAt, int saleQuantity) {
        Product product = products.save(Product.create(productName, price));
        return events.save(FlashSaleEvent.create(product, openAt, saleQuantity));
    }

    @Transactional(readOnly = true)
    public FlashSaleEvent get(long eventId) {
        return events.findById(eventId).orElseThrow(() -> new EventNotFoundException(eventId));
    }
}
