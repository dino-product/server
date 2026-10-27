package com.orbit.notification.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * 한 계정이 받은 알림. 수신 계정·발주사·관련 작업·관련 참여 요청은 다른 모듈의 식별자라 값으로만 둔다. 참여 요청 결과 알림은 관련 참여 요청만, 그 밖의 알림은 관련
 * 작업만 가리킬 수 있다. 만들면 안 읽음이고 읽음은 되돌리지 않는다. 숨긴 알림은 알림센터 어디에도 보이지 않으며 다시 보이게 하지 않는다.
 */
public final class Notification {

    /** 문구의 최대 길이. 가장 긴 문구(작업명 100자와 거절 사유 255자가 들어간 거절 알림)보다 넉넉하다. */
    public static final int MAX_MESSAGE_LENGTH = 500;

    private final Long id;
    private final Long recipientAccountId;
    private final Long organizationId;
    private final NotificationType type;
    private final Long workId;
    private final Long participationRequestId;
    private final String message;
    private final Instant notifiedAt;
    private NotificationStatus status;
    private Instant readAt;
    private boolean hidden;

    private Notification(
            Long id,
            Long recipientAccountId,
            Long organizationId,
            NotificationType type,
            Long workId,
            Long participationRequestId,
            String message,
            Instant notifiedAt,
            NotificationStatus status,
            Instant readAt,
            boolean hidden) {
        this.id = id == null ? null : requirePositive(id, "id");
        this.recipientAccountId = requirePositive(recipientAccountId, "recipientAccountId");
        this.organizationId = requirePositive(organizationId, "organizationId");
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.workId = workId == null ? null : requirePositive(workId, "workId");
        this.participationRequestId = participationRequestId == null
                ? null
                : requirePositive(participationRequestId, "participationRequestId");
        if (type.isParticipationRequestResult() != (participationRequestId != null)) {
            throw new IllegalArgumentException("only participation request results must have participationRequestId");
        }
        if (type.isParticipationRequestResult() && workId != null) {
            throw new IllegalArgumentException("participation request results must not have workId");
        }
        this.message = requireMessage(message);
        this.notifiedAt = Objects.requireNonNull(notifiedAt, "notifiedAt must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        if ((status == NotificationStatus.READ) != (readAt != null)) {
            throw new IllegalArgumentException("readAt must exist only for read notifications");
        }
        this.readAt = readAt;
        this.hidden = hidden;
    }

    /**
     * 참여 요청 결과가 아닌 안 읽은 새 알림을 만든다.
     *
     * @param workId 관련 작업. 작업과 무관한 알림이면 null
     * @param notifiedAt 알림을 일으킨 일이 생긴 시각. 알림센터는 이 시각의 최신순이다
     */
    public static Notification create(
            Long recipientAccountId,
            Long organizationId,
            NotificationType type,
            Long workId,
            String message,
            Instant notifiedAt) {
        return new Notification(
                null,
                recipientAccountId,
                organizationId,
                type,
                workId,
                null,
                message,
                notifiedAt,
                NotificationStatus.UNREAD,
                null,
                false);
    }

    /**
     * 참여 요청 결과의 안 읽은 새 알림을 만든다. 관련 작업은 없다.
     *
     * @param participationRequestId 결과가 난 참여 요청
     * @param notifiedAt 승인·거절 시각
     */
    public static Notification createForParticipationRequest(
            Long recipientAccountId,
            Long organizationId,
            NotificationType type,
            Long participationRequestId,
            String message,
            Instant notifiedAt) {
        return new Notification(
                null,
                recipientAccountId,
                organizationId,
                type,
                null,
                participationRequestId,
                message,
                notifiedAt,
                NotificationStatus.UNREAD,
                null,
                false);
    }

    public static Notification reconstitute(
            Long id,
            Long recipientAccountId,
            Long organizationId,
            NotificationType type,
            Long workId,
            Long participationRequestId,
            String message,
            Instant notifiedAt,
            NotificationStatus status,
            Instant readAt,
            boolean hidden) {
        return new Notification(
                Objects.requireNonNull(id, "id must not be null"),
                recipientAccountId,
                organizationId,
                type,
                workId,
                participationRequestId,
                message,
                notifiedAt,
                status,
                readAt,
                hidden);
    }

    /** 읽음으로 바꾼다. 이미 읽었으면 처음 읽은 시각을 그대로 두고 아무것도 바꾸지 않는다. */
    public void markRead(Instant at) {
        Objects.requireNonNull(at, "at must not be null");
        if (isRead()) {
            return;
        }
        this.status = NotificationStatus.READ;
        this.readAt = at;
    }

    /**
     * 이 시각이나 그 전에 생긴 알림이면 숨긴다. 기사가 거절한 작업의 알림을 거절 시점까지만 숨겨, 그 뒤 같은 작업이 다시 배정돼 생긴 알림은 보이게 한다.
     *
     * @return 이번에 숨겼으면 true. 이미 숨겼거나 그 뒤에 생긴 알림이면 false
     */
    public boolean hideIfNotifiedBy(Instant at) {
        Objects.requireNonNull(at, "at must not be null");
        if (hidden || notifiedAt.isAfter(at)) {
            return false;
        }
        this.hidden = true;
        return true;
    }

    public boolean isReceivedBy(Long accountId) {
        return recipientAccountId.equals(accountId);
    }

    public boolean isRead() {
        return status == NotificationStatus.READ;
    }

    public boolean isHidden() {
        return hidden;
    }

    public Optional<Long> id() {
        return Optional.ofNullable(id);
    }

    public Long recipientAccountId() {
        return recipientAccountId;
    }

    public Long organizationId() {
        return organizationId;
    }

    public NotificationType type() {
        return type;
    }

    public Optional<Long> workId() {
        return Optional.ofNullable(workId);
    }

    public Optional<Long> participationRequestId() {
        return Optional.ofNullable(participationRequestId);
    }

    public String message() {
        return message;
    }

    public Instant notifiedAt() {
        return notifiedAt;
    }

    public NotificationStatus status() {
        return status;
    }

    public Optional<Instant> readAt() {
        return Optional.ofNullable(readAt);
    }

    private static Long requirePositive(Long value, String name) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private static String requireMessage(String message) {
        if (message == null || message.isBlank() || message.length() > MAX_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("message must be 1.." + MAX_MESSAGE_LENGTH + " characters");
        }
        return message;
    }
}
