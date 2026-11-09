package com.flashsale.order.adapter.in.web;

import com.flashsale.order.application.CartService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    CartResponse get(@RequestHeader("X-User-Id") long userId) {
        return CartResponse.from(cartService.view(userId));
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
