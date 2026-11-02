package com.flashsale.order.application;

import com.flashsale.order.client.DownstreamException;
import com.flashsale.order.client.EventInfo;
import com.flashsale.order.client.InventoryClient;
import com.flashsale.order.client.PaymentClient;
import com.flashsale.order.client.PaymentOutcome;
import com.flashsale.order.client.ProductClient;
import com.flashsale.order.client.StockDecreaseResult;
import com.flashsale.order.domain.FailReason;
import com.flashsale.order.domain.OrderRepository;
import com.flashsale.order.domain.OrderStatus;
import java.sql.SQLTransientConnectionException;
import java.time.Clock;
import java.util.EnumMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Deliberately NOT transactional: remote calls must never run inside a DB transaction. */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final ProductClient productClient;
    private final InventoryClient inventoryClient;
    private final PaymentClient paymentClient;
    private final OrderWriter orderWriter;
    private final OrderRepository orderRepository;
    private final PreOrderFailures preOrderFailures;
    private final Clock clock;

    public OrderService(ProductClient productClient, InventoryClient inventoryClient, PaymentClient paymentClient,
                        OrderWriter orderWriter, OrderRepository orderRepository, PreOrderFailures preOrderFailures, Clock clock) {
        this.productClient = productClient;
        this.inventoryClient = inventoryClient;
        this.paymentClient = paymentClient;
        this.orderWriter = orderWriter;
        this.orderRepository = orderRepository;
        this.preOrderFailures = preOrderFailures;
        this.clock = clock;
    }

    public OrderResult placeOrder(long userId, long eventId) {
        EventInfo event;
        try {
            event = productClient.getEvent(eventId);
        } catch (DownstreamException e) {
            throw preOrderFailed(eventId, e.isTimeout() ? FailReason.PRODUCT_TIMEOUT : FailReason.PRODUCT_ERROR, e);
        }
        if (clock.instant().isBefore(event.openAt())) {
            throw new EventNotOpenException(eventId);
        }
        long orderId;
        try {
            orderId = orderWriter.createPending(userId, event).getId();
        } catch (RuntimeException e) {
            if (causedByConnectionTimeout(e)) {
                throw preOrderFailed(eventId, FailReason.ORDER_DB_TIMEOUT, e);
            }
            throw e;
        }

        StockDecreaseResult stock;
        try {
            stock = inventoryClient.decrease(event.productId(), 1);
        } catch (DownstreamException e) {
            log.warn("inventory decrease outcome unknown for order {}", orderId, e);
            return fail(orderId, e.isTimeout() ? FailReason.INVENTORY_TIMEOUT : FailReason.INVENTORY_ERROR);
        }
        if (stock == StockDecreaseResult.CONFLICT) {
            return fail(orderId, FailReason.STOCK_CONFLICT);
        }
        if (stock == StockDecreaseResult.OUT_OF_STOCK) {
            return fail(orderId, FailReason.SOLD_OUT);
        }

        PaymentOutcome outcome;
        try {
            outcome = paymentClient.pay(orderId, userId, event.price());
        } catch (DownstreamException e) {
            // outcome unknown: do NOT restore stock
            log.warn("payment outcome unknown for order {}", orderId, e);
            return fail(orderId, e.isTimeout() ? FailReason.PAYMENT_TIMEOUT : FailReason.PAYMENT_ERROR);
        }
        if (outcome == PaymentOutcome.DECLINED) {
            try {
                inventoryClient.increase(event.productId(), 1);
            } catch (DownstreamException e) {
                log.error("stock restore failed for declined order {}", orderId, e);
            }
            return fail(orderId, FailReason.PAYMENT_DECLINED);
        }

        orderWriter.markPaid(orderId);
        return new OrderResult(orderId, OrderStatus.PAID, null);
    }

    public OrderStats stats(long eventId) {
        long paid = 0;
        long failed = 0;
        long pending = 0;
        for (var c : orderRepository.countByStatus(eventId)) {
            switch (c.getStatus()) {
                case PAID -> paid = c.getCnt();
                case FAILED -> failed = c.getCnt();
                case PENDING -> pending = c.getCnt();
            }
        }
        Map<FailReason, Long> byReason = new EnumMap<>(FailReason.class);
        for (var c : orderRepository.countByFailReason(eventId)) {
            if (c.getReason() != null && c.getCnt() > 0) {
                byReason.put(c.getReason(), c.getCnt());
            }
        }
        return new OrderStats(eventId, paid, failed, pending, byReason, preOrderFailures.snapshot(eventId));
    }

    public void deleteAll() {
        orderRepository.deleteAllInBatch();
        preOrderFailures.reset();
    }

    private OrderResult fail(long orderId, FailReason reason) {
        orderWriter.markFailed(orderId, reason);
        return new OrderResult(orderId, OrderStatus.FAILED, reason);
    }

    private PreOrderFailedException preOrderFailed(long eventId, FailReason reason, Throwable cause) {
        preOrderFailures.record(eventId, reason);
        return new PreOrderFailedException(reason, cause);
    }

    private static boolean causedByConnectionTimeout(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof SQLTransientConnectionException) {
                return true;
            }
        }
        return false;
    }
}
