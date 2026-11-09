package com.flashsale.order.adapter.in.web;

import com.flashsale.order.application.OrderResult;
import com.flashsale.order.application.OrderQueryService;
import com.flashsale.order.application.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@Validated
public class OrderController {

    private final OrderService orderService;
    private final OrderQueryService queryService;

    public OrderController(OrderService orderService, OrderQueryService queryService) {
        this.orderService = orderService;
        this.queryService = queryService;
    }

    @PostMapping
    ResponseEntity<OrderResponse> place(@RequestHeader("X-User-Id") long userId,
                                        @Valid @RequestBody PlaceOrderRequest request) {
        OrderResult result = orderService.placeOrder(userId, request.eventId());
        return ResponseEntity.status(FailReasonStatus.of(result)).body(OrderResponse.from(result));
    }

    @GetMapping
    OrderHistoryResponses.Page list(@RequestHeader("X-User-Id") long userId,
                                    @RequestParam(defaultValue = "0") @Min(0) int page,
                                    @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return OrderHistoryResponses.Page.from(queryService.myOrders(userId, page, size));
    }

    @GetMapping("/{orderId}")
    OrderHistoryResponses.Detail get(@RequestHeader("X-User-Id") long userId, @PathVariable long orderId) {
        return OrderHistoryResponses.Detail.from(queryService.myOrder(userId, orderId));
    }
}
