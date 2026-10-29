package com.flashsale.order.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OrderRepository extends JpaRepository<Order, Long> {

    interface StatusCount {
        OrderStatus getStatus();
        long getCnt();
    }

    interface ReasonCount {
        FailReason getReason();
        long getCnt();
    }

    @Query("select o.status as status, count(o) as cnt from Order o where o.eventId = :eventId group by o.status")
    List<StatusCount> countByStatus(long eventId);

    @Query("select o.failReason as reason, count(o) as cnt from Order o "
            + "where o.eventId = :eventId and o.status = com.flashsale.order.domain.OrderStatus.FAILED "
            + "group by o.failReason")
    List<ReasonCount> countByFailReason(long eventId);
}
