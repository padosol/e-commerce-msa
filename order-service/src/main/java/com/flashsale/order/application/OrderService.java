package com.flashsale.order.application;

import com.flashsale.order.application.port.out.DownstreamException;
import com.flashsale.order.application.port.out.EventInfo;
import com.flashsale.order.application.port.out.InventoryPort;
import com.flashsale.order.application.port.out.OrderRepository;
import com.flashsale.order.application.port.out.PaymentOutcome;
import com.flashsale.order.application.port.out.PaymentPort;
import com.flashsale.order.application.port.out.ProductPort;
import com.flashsale.order.application.port.out.StockDecreaseResult;
import com.flashsale.order.domain.FailReason;
import com.flashsale.order.domain.Order;
import com.flashsale.order.domain.OrderLine;
import com.flashsale.order.domain.OrderStatus;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Deliberately NOT transactional: remote calls must never run inside a DB transaction. */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final ProductPort productPort;
    private final InventoryPort inventoryPort;
    private final PaymentPort paymentPort;
    private final OrderWriter orderWriter;
    private final OrderRepository orderRepository;
    private final PreOrderFailures preOrderFailures;
    private final Clock clock;

    public OrderService(ProductPort productPort, InventoryPort inventoryPort, PaymentPort paymentPort,
                        OrderWriter orderWriter, OrderRepository orderRepository, PreOrderFailures preOrderFailures, Clock clock) {
        this.productPort = productPort;
        this.inventoryPort = inventoryPort;
        this.paymentPort = paymentPort;
        this.orderWriter = orderWriter;
        this.orderRepository = orderRepository;
        this.preOrderFailures = preOrderFailures;
        this.clock = clock;
    }

    public OrderResult placeOrder(long userId, long eventId) {
        EventInfo event;
        try {
            event = productPort.getEvent(eventId);
        } catch (DownstreamException e) {
            throw preOrderFailed(eventId, e.isTimeout() ? FailReason.PRODUCT_TIMEOUT : FailReason.PRODUCT_ERROR, e);
        }
        if (clock.instant().isBefore(event.openAt())) {
            throw new EventNotOpenException(eventId);
        }
        Order pending = Order.pending(userId, event.eventId(),
                List.of(new OrderLine(event.productId(), event.productName(), event.price(), 1)), clock.instant());
        long orderId;
        try {
            orderId = orderWriter.createPending(pending).id();
        } catch (RuntimeException e) {
            if (ConnectionTimeouts.isCausing(e)) {
                throw preOrderFailed(eventId, FailReason.ORDER_DB_TIMEOUT, e);
            }
            throw e;
        }

        StockDecreaseResult stock;
        try {
            stock = inventoryPort.decrease(event.productId(), 1);
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
            outcome = paymentPort.pay(orderId, userId, pending.amount());
        } catch (DownstreamException e) {
            // outcome unknown: do NOT restore stock
            log.warn("payment outcome unknown for order {}", orderId, e);
            return fail(orderId, e.isTimeout() ? FailReason.PAYMENT_TIMEOUT : FailReason.PAYMENT_ERROR);
        }
        if (outcome == PaymentOutcome.DECLINED) {
            try {
                inventoryPort.increase(event.productId(), 1);
            } catch (DownstreamException e) {
                log.error("stock restore failed for declined order {}", orderId, e);
            }
            return fail(orderId, FailReason.PAYMENT_DECLINED);
        }

        orderWriter.markPaid(orderId);
        return new OrderResult(orderId, OrderStatus.PAID, null);
    }

    public OrderStats stats(long eventId) {
        Map<OrderStatus, Long> byStatus = orderRepository.countByStatus(eventId);
        return new OrderStats(eventId,
                byStatus.getOrDefault(OrderStatus.PAID, 0L),
                byStatus.getOrDefault(OrderStatus.FAILED, 0L),
                byStatus.getOrDefault(OrderStatus.PENDING, 0L),
                orderRepository.countFailedByReason(eventId),
                preOrderFailures.snapshot(eventId));
    }

    public void deleteAll() {
        orderRepository.deleteAll();
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
}
