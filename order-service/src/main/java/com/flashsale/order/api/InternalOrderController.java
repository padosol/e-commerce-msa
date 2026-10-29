package com.flashsale.order.api;

import com.flashsale.order.application.OrderService;
import com.flashsale.order.application.OrderStats;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/orders")
public class InternalOrderController {

    private final OrderService orderService;

    public InternalOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/stats")
    OrderStats stats(@RequestParam long eventId) {
        return orderService.stats(eventId);
    }

    @DeleteMapping
    ResponseEntity<Void> deleteAll() {
        orderService.deleteAll();
        return ResponseEntity.noContent().build();
    }
}
