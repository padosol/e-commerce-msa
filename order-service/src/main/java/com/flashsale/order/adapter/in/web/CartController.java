package com.flashsale.order.adapter.in.web;

import com.flashsale.order.application.CartService;
import com.flashsale.order.application.CheckoutService;
import com.flashsale.order.application.OrderResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;
    private final CheckoutService checkoutService;

    public CartController(CartService cartService, CheckoutService checkoutService) {
        this.cartService = cartService;
        this.checkoutService = checkoutService;
    }

    @GetMapping
    CartResponse get(@RequestHeader("X-User-Id") long userId) {
        return CartResponse.from(cartService.view(userId));
    }

    @PostMapping("/checkout")
    ResponseEntity<OrderResponse> checkout(@RequestHeader("X-User-Id") long userId) {
        OrderResult result = checkoutService.checkout(userId);
        return ResponseEntity.status(FailReasonStatus.of(result)).body(OrderResponse.from(result));
    }

    @PutMapping("/items/{productId}")
    CartResponse put(@RequestHeader("X-User-Id") long userId, @PathVariable long productId,
                     @Valid @RequestBody PutCartItemRequest request) {
        return CartResponse.from(cartService.put(userId, productId, request.quantity()));
    }

    @DeleteMapping("/items/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(@RequestHeader("X-User-Id") long userId, @PathVariable long productId) {
        cartService.remove(userId, productId);
    }
}
