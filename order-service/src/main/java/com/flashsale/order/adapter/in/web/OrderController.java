package com.flashsale.order.adapter.in.web;

import com.flashsale.order.application.OrderResult;
import com.flashsale.order.application.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    ResponseEntity<OrderResponse> place(@RequestHeader("X-User-Id") long userId,
                                        @Valid @RequestBody PlaceOrderRequest request) {
        OrderResult result = orderService.placeOrder(userId, request.eventId());
        return ResponseEntity.status(statusOf(result)).body(OrderResponse.from(result));
    }

    private static HttpStatus statusOf(OrderResult r) {
        return switch (r.status()) {
            case PAID -> HttpStatus.CREATED;
            case FAILED -> FailReasonStatus.of(r.reason());
            case PENDING -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
