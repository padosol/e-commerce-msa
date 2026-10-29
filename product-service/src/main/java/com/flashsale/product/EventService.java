package com.flashsale.product;

import com.flashsale.product.domain.FlashSaleEvent;
import com.flashsale.product.domain.FlashSaleEventRepository;
import com.flashsale.product.domain.Product;
import com.flashsale.product.domain.ProductRepository;
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
        Product product = products.save(new Product(productName, price));
        return events.save(new FlashSaleEvent(product, openAt, saleQuantity));
    }

    @Transactional(readOnly = true)
    public FlashSaleEvent get(long eventId) {
        return events.findWithProductById(eventId).orElseThrow(() -> new EventNotFoundException(eventId));
    }
}
