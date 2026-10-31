package com.flashsale.product.api;

import com.flashsale.product.EventService;
import com.flashsale.product.domain.FlashSaleEvent;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EventController {

    private final EventService service;

    public EventController(EventService service) {
        this.service = service;
    }

    @PostMapping("/internal/events")
    @ResponseStatus(HttpStatus.CREATED)
    CreateEventResponse create(@Valid @RequestBody CreateEventRequest request) {
        FlashSaleEvent e = service.create(
            request.productName(), request.price(), request.openAt(), request.saleQuantity());
        return new CreateEventResponse(e.getId(), e.getProduct().getId());
    }

    @GetMapping("/api/products/events/{eventId}")
    EventResponse get(@PathVariable long eventId) {
        return EventResponse.from(service.get(eventId));
    }
}
