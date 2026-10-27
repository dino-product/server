package com.orbit.notification.domain;

import java.time.Instant;
import java.util.Optional;

/**
 * 알림 한 건을 한 채널로 보내는 발신 대기의 애그리게잇 루트. 알림센터의 알림({@link Notification})과 별개로 발신 상태만 가지며 알림은 식별자로만
 * 가리킨다. 다시 보낼 실패는 횟수 제한 없이 대기로 되돌리고, 다시 보내도 성공할 수 없을 때(기기 없음 → {@link DeliveryStatus#SKIPPED}, 영구 실패 →
 * {@link DeliveryStatus#FAILED})만 끝낸다.
 *
 * <p>보내는 동안({@link DeliveryStatus#IN_FLIGHT}) {@code nextAttemptAt}은 점유 만료 시각이다. 그때까지 결과가 없으면(발신 중 인스턴스 중단) 다시 집을 수
 * 있다. {@code attempts}는 시작한 발신 횟수이며 결과를 기록해도 바뀌지 않으므로, 저장소는 집은 시점의 값과 비교해 다른 인스턴스가 다시 집은 뒤 늦게 도착한 결과를
 * 가려낸다.
 */
public final class NotificationDelivery {

    public static final int MAX_ERROR_LENGTH = 500;

    private final NotificationDeliveryId id;
    private final Long notificationId;
    private final Long recipientAccountId;
    private final DeliveryChannel channel;
    private final DeliveryStatus status;
    private final int attempts;
    private final Instant nextAttemptAt;
    private final String lastError;
    private final String providerMessageId;
    private final Instant sentAt;

    private NotificationDelivery(
            NotificationDeliveryId id,
            Long notificationId,
            Long recipientAccountId,
            DeliveryChannel channel,
            DeliveryStatus status,
            int attempts,
            Instant nextAttemptAt,
            String lastError,
            String providerMessageId,
            Instant sentAt) {
        if (notificationId == null || notificationId <= 0) {
            throw new IllegalArgumentException("notificationId must be positive");
        }
        if (recipientAccountId == null || recipientAccountId <= 0) {
            throw new IllegalArgumentException("recipientAccountId must be positive");
        }
        if (channel == null) {
            throw new IllegalArgumentException("channel must not be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        if (attempts < 0) {
            throw new IllegalArgumentException("attempts must not be negative");
        }
        if (nextAttemptAt == null) {
            throw new IllegalArgumentException("nextAttemptAt must not be null");
        }
        if (status == DeliveryStatus.SENT && sentAt == null) {
            throw new IllegalArgumentException("sent delivery must have sentAt");
        }
        this.id = id;
        this.notificationId = notificationId;
        this.recipientAccountId = recipientAccountId;
        this.channel = channel;
        this.status = status;
        this.attempts = attempts;
        this.nextAttemptAt = nextAttemptAt;
        this.lastError = truncate(lastError);
        this.providerMessageId = providerMessageId;
        this.sentAt = sentAt;
    }

    /** 기사 앱 푸시 발신 대기를 만든다. 바로 보낼 수 있도록 다음 시도 시각을 지금으로 둔다. */
    public static NotificationDelivery pendingPush(Long notificationId, Long recipientAccountId, Instant now) {
        return new NotificationDelivery(
                null,
                notificationId,
                recipientAccountId,
                DeliveryChannel.PUSH,
                DeliveryStatus.PENDING,
                0,
                now,
                null,
                null,
                null);
    }

    public static NotificationDelivery reconstitute(
            NotificationDeliveryId id,
            Long notificationId,
            Long recipientAccountId,
            DeliveryChannel channel,
            DeliveryStatus status,
            int attempts,
            Instant nextAttemptAt,
            String lastError,
            String providerMessageId,
            Instant sentAt) {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        return new NotificationDelivery(
                id,
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

    /** 대기 중이거나 점유가 만료된 발신이면 {@code now}에 집을 수 있다. */
    public boolean isDue(Instant now) {
        return (status == DeliveryStatus.PENDING || status == DeliveryStatus.IN_FLIGHT) && !nextAttemptAt.isAfter(now);
    }

    /** 보내기 시작한다. 시도 횟수를 늘리고 {@code leaseUntil}까지 다른 인스턴스가 집지 못하게 점유한다. */
    public NotificationDelivery claim(Instant now, Instant leaseUntil) {
        if (!isDue(now)) {
            throw new IllegalStateException("delivery is not due: " + status + " until " + nextAttemptAt);
        }
        return with(DeliveryStatus.IN_FLIGHT, attempts + 1, leaseUntil, lastError, null, null);
    }

    public NotificationDelivery markSent(String providerMessageId, Instant sentAt) {
        requireInFlight();
        return with(DeliveryStatus.SENT, attempts, nextAttemptAt, lastError, providerMessageId, sentAt);
    }

    /** 다시 보낼 실패를 기록하고 {@code nextAttemptAt}에 다시 보내도록 대기로 되돌린다. */
    public NotificationDelivery retryAt(String error, Instant nextAttemptAt) {
        requireInFlight();
        return with(DeliveryStatus.PENDING, attempts, nextAttemptAt, error, null, null);
    }

    /** 받을 기기가 없어 보내지 않고 끝낸다. */
    public NotificationDelivery skip(String reason) {
        requireInFlight();
        return with(DeliveryStatus.SKIPPED, attempts, nextAttemptAt, reason, null, null);
    }

    /** 다시 보내도 성공할 수 없는 실패로 끝낸다. */
    public NotificationDelivery fail(String error) {
        requireInFlight();
        return with(DeliveryStatus.FAILED, attempts, nextAttemptAt, error, null, null);
    }

    public Optional<NotificationDeliveryId> id() {
        return Optional.ofNullable(id);
    }

    public Long notificationId() {
        return notificationId;
    }

    public Long recipientAccountId() {
        return recipientAccountId;
    }

    public DeliveryChannel channel() {
        return channel;
    }

    public DeliveryStatus status() {
        return status;
    }

    public int attempts() {
        return attempts;
    }

    public Instant nextAttemptAt() {
        return nextAttemptAt;
    }

    public Optional<String> lastError() {
        return Optional.ofNullable(lastError);
    }

    public Optional<String> providerMessageId() {
        return Optional.ofNullable(providerMessageId);
    }

    public Optional<Instant> sentAt() {
        return Optional.ofNullable(sentAt);
    }

    private void requireInFlight() {
        if (status != DeliveryStatus.IN_FLIGHT) {
            throw new IllegalStateException("delivery is not in flight: " + status);
        }
    }

    private NotificationDelivery with(
            DeliveryStatus status,
            int attempts,
            Instant nextAttemptAt,
            String lastError,
            String providerMessageId,
            Instant sentAt) {
        return new NotificationDelivery(
                id,
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

    private static String truncate(String error) {
        if (error == null || error.length() <= MAX_ERROR_LENGTH) {
            return error;
        }
        return error.substring(0, MAX_ERROR_LENGTH);
    }
}
