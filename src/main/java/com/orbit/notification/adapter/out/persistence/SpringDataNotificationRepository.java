package com.orbit.notification.adapter.out.persistence;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.orbit.notification.domain.NotificationStatus;
import com.orbit.notification.domain.NotificationType;

interface SpringDataNotificationRepository extends JpaRepository<NotificationJpaEntity, Long> {

    Page<NotificationJpaEntity> findByRecipientAccountIdAndHiddenFalse(Long recipientAccountId, Pageable pageable);

    Page<NotificationJpaEntity> findByRecipientAccountIdAndStatusAndHiddenFalse(
            Long recipientAccountId, NotificationStatus status, Pageable pageable);

    long countByRecipientAccountIdAndStatusAndHiddenFalse(Long recipientAccountId, NotificationStatus status);

    List<NotificationJpaEntity> findByRecipientAccountIdAndWorkIdOrderByIdAsc(Long recipientAccountId, Long workId);

    boolean existsByRecipientAccountIdAndTypeAndWorkIdAndNotifiedAt(
            Long recipientAccountId, NotificationType type, Long workId, Instant notifiedAt);

    boolean existsByRecipientAccountIdAndTypeAndParticipationRequestId(
            Long recipientAccountId, NotificationType type, Long participationRequestId);
}
