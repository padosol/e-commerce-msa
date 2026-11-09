package com.flashsale.order.adapter.out.persistence;

import com.flashsale.order.domain.FailReason;
import com.flashsale.order.domain.OrderStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Pageable;
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

    @EntityGraph(attributePaths = "lines")
    Optional<OrderJpaEntity> findWithLinesByIdAndUserId(long id, long userId);

    @Query("select o.id from OrderJpaEntity o where o.userId = :userId order by o.id desc")
    List<Long> findIdsByUserId(long userId, Pageable pageable);

    @EntityGraph(attributePaths = "lines")
    @Query("select o from OrderJpaEntity o where o.id in :ids order by o.id desc")
    List<OrderJpaEntity> findWithLinesByIdIn(List<Long> ids);

    long countByUserId(long userId);

    @Query("select o.status as status, count(o) as cnt from OrderJpaEntity o where o.eventId = :eventId "
            + "group by o.status")
    List<StatusCount> countByStatus(long eventId);

    @Query("select o.failReason as reason, count(o) as cnt from OrderJpaEntity o "
            + "where o.eventId = :eventId and o.status = com.flashsale.order.domain.OrderStatus.FAILED "
            + "group by o.failReason")
    List<ReasonCount> countByFailReason(long eventId);
}
