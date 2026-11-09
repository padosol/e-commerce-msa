package com.flashsale.order.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.flashsale.order.TestcontainersConfiguration;
import com.flashsale.order.application.port.out.OrderRepository;
import com.flashsale.order.domain.FailReason;
import com.flashsale.order.domain.Order;
import com.flashsale.order.domain.OrderLine;
import com.flashsale.order.domain.OrderStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class OrderPersistenceAdapterTest {

    static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");
    static final Instant LATER = Instant.parse("2026-11-01T10:00:05Z");

    @Autowired
    OrderRepository repository;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    private List<Long> itemIds(long orderId) {
        return jdbc.queryForList("select id from order_items where order_id = ? order by id", Long.class, orderId);
    }

    @Test
    void savesLinesAndRestoresThem() {
        List<OrderLine> lines = List.of(new OrderLine(7, "A", 1000, 2), new OrderLine(8, "B", 500, 1));

        Order saved = repository.save(Order.pending(5, null, lines, NOW));
        Order found = repository.findById(saved.id()).orElseThrow();

        assertThat(saved.id()).isNotNull();
        assertThat(found.lines()).isEqualTo(lines);
        assertThat(found.amount()).isEqualTo(2500);
        assertThat(found.eventId()).isNull();
        assertThat(found.userId()).isEqualTo(5);
        assertThat(found.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(found.createdAt()).isEqualTo(NOW);
        assertThat(jdbc.queryForObject("select amount from orders where id = ?", Long.class, saved.id()))
                .isEqualTo(2500L);
    }

    @Test
    void statusChangeKeepsOrderItems() {
        Order saved = repository.save(Order.pending(5, 1L,
                List.of(new OrderLine(7, "A", 1000, 1), new OrderLine(8, "B", 500, 2)), NOW));
        List<Long> before = itemIds(saved.id());

        saved.markPaid(LATER);
        repository.save(saved);

        assertThat(before).hasSize(2);
        assertThat(itemIds(saved.id())).isEqualTo(before);
        Order found = repository.findById(saved.id()).orElseThrow();
        assertThat(found.status()).isEqualTo(OrderStatus.PAID);
        assertThat(found.updatedAt()).isEqualTo(LATER);
        assertThat(found.createdAt()).isEqualTo(NOW);
        assertThat(found.lines()).hasSize(2);
    }

    private void save(long eventId, OrderStatus status, FailReason reason) {
        Order order = Order.pending(5, eventId, List.of(new OrderLine(7, "A", 1000, 1)), NOW);
        if (status == OrderStatus.PAID) {
            order.markPaid(NOW);
        } else if (status == OrderStatus.FAILED) {
            order.markFailed(reason, NOW);
        }
        repository.save(order);
    }

    @Test
    void statsCountByEventAndReason() {
        save(1, OrderStatus.PAID, null);
        save(1, OrderStatus.PAID, null);
        save(1, OrderStatus.FAILED, FailReason.SOLD_OUT);
        save(2, OrderStatus.PAID, null);

        assertThat(repository.countByStatus(1)).isEqualTo(Map.of(OrderStatus.PAID, 2L, OrderStatus.FAILED, 1L));
        assertThat(repository.countFailedByReason(1)).isEqualTo(Map.of(FailReason.SOLD_OUT, 1L));
        assertThat(repository.countByStatus(2)).isEqualTo(Map.of(OrderStatus.PAID, 1L));
    }
}
