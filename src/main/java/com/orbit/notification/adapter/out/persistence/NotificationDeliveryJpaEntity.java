package com.orbit.notification.adapter.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import com.orbit.notification.domain.DeliveryChannel;
import com.orbit.notification.domain.DeliveryStatus;
import com.orbit.notification.domain.NotificationDelivery;
import com.orbit.notification.domain.NotificationDeliveryId;
import com.orbit.shared.persistence.BaseTimeEntity;

/**
 * 알림 발신 대기의 JPA Entity. 값 검증은 Domain이 하며 이 Entity는 컬럼 제약만 선언한다. 스키마는 Flyway 마이그레이션이 만든다.
 * {@code notification_id}는 같은 모듈의 알림을 가리키지만 다른 애그리게잇이라 외래 키 없이 식별자로만 둔다.
 */
@Entity
@Table(
        name = "notification_deliveries",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_notification_deliveries_notification_channel",
                        columnNames = {"notification_id", "channel"}),
        indexes =
                @Index(name = "ix_notification_deliveries_status_next_attempt", columnList = "status, next_attempt_at"))
class NotificationDeliveryJpaEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "notification_id", nullable = false)
    private Long notificationId;

    @Column(name = "recipient_account_id", nullable = false)
    private Long recipientAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private DeliveryChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DeliveryStatus status;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_error", length = NotificationDelivery.MAX_ERROR_LENGTH)
    private String lastError;

    @Column(name = "provider_message_id")
    private String providerMessageId;

    @Column(name = "sent_at")
    private Instant sentAt;

    protected NotificationDeliveryJpaEntity() {}

    static NotificationDeliveryJpaEntity from(NotificationDelivery delivery) {
        if (delivery.id().isPresent()) {
            throw new IllegalArgumentException(
                    "delivery already has an id: " + delivery.id().get().value());
        }
        NotificationDeliveryJpaEntity entity = new NotificationDeliveryJpaEntity();
        entity.notificationId = delivery.notificationId();
        entity.recipientAccountId = delivery.recipientAccountId();
        entity.channel = delivery.channel();
        entity.apply(delivery);
        return entity;
    }

    /** 발신 상태를 Domain 값으로 바꾼다. 알림·수신 계정·채널은 바뀌지 않는다. */
    void apply(NotificationDelivery delivery) {
        status = delivery.status();
        attempts = delivery.attempts();
        nextAttemptAt = delivery.nextAttemptAt();
        lastError = delivery.lastError().orElse(null);
        providerMessageId = delivery.providerMessageId().orElse(null);
        sentAt = delivery.sentAt().orElse(null);
    }

    NotificationDelivery toDomain() {
        return NotificationDelivery.reconstitute(
                new NotificationDeliveryId(id),
                notificationId,
                recipientAccountId,
                channel,
                status,
                attempts,
                nextAttemptAt,
                lastError,
                providerMessageId,
                sentAt);
    }

    DeliveryStatus status() {
        return status;
    }

    int attempts() {
        return attempts;
    }
}
