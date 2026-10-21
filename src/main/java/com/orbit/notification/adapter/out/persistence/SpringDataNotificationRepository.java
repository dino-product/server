package com.orbit.notification.adapter.out.persistence;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.orbit.notification.domain.NotificationStatus;

interface SpringDataNotificationRepository extends JpaRepository<NotificationJpaEntity, Long> {

    Page<NotificationJpaEntity> findByRecipientAccountIdAndHiddenFalse(Long recipientAccountId, Pageable pageable);

    Page<NotificationJpaEntity> findByRecipientAccountIdAndStatusAndHiddenFalse(
            Long recipientAccountId, NotificationStatus status, Pageable pageable);

    long countByRecipientAccountIdAndStatusAndHiddenFalse(Long recipientAccountId, NotificationStatus status);

    List<NotificationJpaEntity> findByRecipientAccountIdAndWorkIdOrderByIdAsc(Long recipientAccountId, Long workId);
}
