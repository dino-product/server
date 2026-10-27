package com.orbit.notification.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataNotificationDeliveryRepository extends JpaRepository<NotificationDeliveryJpaEntity, Long> {

    /** 보낼 때가 된 대기·점유 만료 행을 잠그며 읽는다. 다른 트랜잭션이 잠근 행은 기다리지 않고 건너뛴다. */
    @Query(value = """
            select * from notification_deliveries
            where status in ('PENDING', 'IN_FLIGHT') and next_attempt_at <= :now
            order by next_attempt_at, id
            limit :limit
            for update skip locked
            """, nativeQuery = true)
    List<NotificationDeliveryJpaEntity> lockDue(@Param("now") Instant now, @Param("limit") int limit);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from NotificationDeliveryJpaEntity d where d.id = :id")
    Optional<NotificationDeliveryJpaEntity> findLockedById(@Param("id") Long id);
}
