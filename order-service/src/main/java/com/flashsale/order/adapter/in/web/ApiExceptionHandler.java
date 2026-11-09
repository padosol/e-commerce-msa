package com.flashsale.order.adapter.in.web;

import com.flashsale.order.application.EventNotOpenException;
import com.flashsale.order.application.PreOrderFailedException;
import com.flashsale.order.application.port.out.DownstreamException;
import com.flashsale.order.application.port.out.EventNotFoundException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(EventNotFoundException.class)
    ResponseEntity<Map<String, String>> eventNotFound(EventNotFoundException e) {
        return body(HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(EventNotOpenException.class)
    ResponseEntity<Map<String, String>> eventNotOpen(EventNotOpenException e) {
        return body(HttpStatus.BAD_REQUEST, "EVENT_NOT_OPEN", e.getMessage());
    }

    @ExceptionHandler(PreOrderFailedException.class)
    ResponseEntity<Map<String, String>> preOrderFailed(PreOrderFailedException e) {
        String reason = e.reason().name();
        return ResponseEntity.status(FailReasonStatus.of(e.reason()))
                .body(Map.of("code", reason, "reason", reason, "message", "order failed before creation: " + reason));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    ResponseEntity<Map<String, String>> unauthorized(MissingRequestHeaderException e) {
        return body(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "X-User-Id header is required");
    }

    @ExceptionHandler(DownstreamException.class)
    ResponseEntity<Map<String, String>> downstream(DownstreamException e) {
        return body(HttpStatus.BAD_GATEWAY, "DOWNSTREAM_ERROR", e.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    ResponseEntity<Map<String, String>> invalid(Exception e) {
        return body(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "request is invalid");
    }

    private static ResponseEntity<Map<String, String>> body(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of("code", code, "message", message));
    }
}
