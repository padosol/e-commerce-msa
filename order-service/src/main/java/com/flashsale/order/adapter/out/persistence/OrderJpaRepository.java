package com.flashsale.order.adapter.out.persistence;

import com.flashsale.order.domain.FailReason;
import com.flashsale.order.domain.OrderStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OrderJpaRepository extends JpaRepository<OrderJpaEntity, Long> {

    interface StatusCount {
        OrderStatus getStatus();
        long getCnt();
    }

    interface ReasonCount {
        FailReason getReason();
        long getCnt();
    }

    /** The order and its lines in one query. */
    @EntityGraph(attributePaths = "lines")
    Optional<OrderJpaEntity> findWithLinesById(long id);

    @Query("select o.status as status, count(o) as cnt from OrderJpaEntity o where o.eventId = :eventId "
            + "group by o.status")
    List<StatusCount> countByStatus(long eventId);

    @Query("select o.failReason as reason, count(o) as cnt from OrderJpaEntity o "
            + "where o.eventId = :eventId and o.status = com.flashsale.order.domain.OrderStatus.FAILED "
            + "group by o.failReason")
    List<ReasonCount> countByFailReason(long eventId);
}
