package com.orbit.notification.adapter.out.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataNotificationSettingRepository extends JpaRepository<NotificationSettingJpaEntity, Long> {

    Optional<NotificationSettingJpaEntity> findByAccountId(Long accountId);
}
