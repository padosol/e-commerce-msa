package com.flashsale.order.application;

import com.flashsale.order.application.port.out.CartRepository;
import com.flashsale.order.application.port.out.CatalogProduct;
import com.flashsale.order.application.port.out.DownstreamException;
import com.flashsale.order.application.port.out.InventoryPort;
import com.flashsale.order.application.port.out.PaymentOutcome;
import com.flashsale.order.application.port.out.PaymentPort;
import com.flashsale.order.application.port.out.ProductPort;
import com.flashsale.order.application.port.out.StockDecreaseResult;
import com.flashsale.order.application.port.out.StockLine;
import com.flashsale.order.domain.Cart;
import com.flashsale.order.domain.CartItem;
import com.flashsale.order.domain.FailReason;
import com.flashsale.order.domain.Order;
import com.flashsale.order.domain.OrderLine;
import com.flashsale.order.domain.OrderStatus;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Deliberately NOT transactional: remote calls must never run inside a DB transaction. */
@Service
public class CheckoutService {

    private static final Logger log = LoggerFactory.getLogger(CheckoutService.class);

    private final CartRepository carts;
    private final ProductPort productPort;
    private final InventoryPort inventoryPort;
    private final PaymentPort paymentPort;
    private final OrderWriter orderWriter;
    private final Clock clock;

    public CheckoutService(CartRepository carts, ProductPort productPort, InventoryPort inventoryPort,
                           PaymentPort paymentPort, OrderWriter orderWriter, Clock clock) {
        this.carts = carts;
        this.productPort = productPort;
        this.inventoryPort = inventoryPort;
        this.paymentPort = paymentPort;
        this.orderWriter = orderWriter;
        this.clock = clock;
    }

    public OrderResult checkout(long userId) {
        List<CartItem> items = carts.findByUserId(userId).items();
        if (items.isEmpty()) {
            throw new CartEmptyException();
        }
        List<OrderLine> orderLines = priced(items);
        List<StockLine> stockLines = items.stream().map(i -> new StockLine(i.productId(), i.quantity())).toList();

        Order pending = Order.pending(userId, null, orderLines, clock.instant());
        long orderId;
        try {
            orderId = orderWriter.createPending(pending).id();
        } catch (RuntimeException e) {
            if (ConnectionTimeouts.isCausing(e)) {
                throw new PreOrderFailedException(FailReason.ORDER_DB_TIMEOUT, e);
            }
            throw e;
        }

        StockDecreaseResult stock;
        try {
            stock = inventoryPort.decreaseAll(stockLines);
        } catch (DownstreamException e) {
            // outcome unknown: do NOT restore stock
            log.warn("inventory decrease-all outcome unknown for order {}", orderId, e);
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
                inventoryPort.increaseAll(stockLines);
            } catch (DownstreamException e) {
                log.error("stock restore failed for declined order {}", orderId, e);
            }
            return fail(orderId, FailReason.PAYMENT_DECLINED);
        }

        orderWriter.markPaid(orderId);
        removeOrderedLines(userId, orderLines, orderId);
        return new OrderResult(orderId, OrderStatus.PAID, null);
    }

    private List<OrderLine> priced(List<CartItem> items) {
        Map<Long, CatalogProduct> listed = new HashMap<>();
        try {
            productPort.findListed(items.stream().map(CartItem::productId).toList())
                    .forEach(p -> listed.put(p.productId(), p));
        } catch (DownstreamException e) {
            throw new PreOrderFailedException(e.isTimeout() ? FailReason.PRODUCT_TIMEOUT : FailReason.PRODUCT_ERROR, e);
        }
        return items.stream().map(item -> {
            CatalogProduct p = listed.get(item.productId());
            if (p == null) {
                throw new ProductUnavailableException(item.productId());
            }
            return new OrderLine(p.productId(), p.name(), p.price(), item.quantity());
        }).toList();
    }

    /** Re-reads the cart so products added during the checkout stay. The order is already PAID, so a failure here only logs. */
    private void removeOrderedLines(long userId, List<OrderLine> ordered, long orderId) {
        try {
            Cart cart = carts.findByUserId(userId);
            cart.removeAll(ordered.stream().map(OrderLine::productId).toList());
            carts.save(cart);
        } catch (RuntimeException e) {
            log.error("cart not cleared after paid order {}", orderId, e);
        }
    }

    private OrderResult fail(long orderId, FailReason reason) {
        orderWriter.markFailed(orderId, reason);
        return new OrderResult(orderId, OrderStatus.FAILED, reason);
    }
}
