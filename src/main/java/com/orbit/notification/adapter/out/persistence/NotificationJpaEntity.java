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

import org.hibernate.annotations.DynamicUpdate;

import com.orbit.notification.domain.Notification;
import com.orbit.notification.domain.NotificationStatus;
import com.orbit.notification.domain.NotificationType;
import com.orbit.shared.persistence.BaseTimeEntity;

/**
 * 알림. 수신 계정·발주사·작업은 다른 모듈의 식별자라 ID로만 둔다. 알림센터 목록(수신 계정의 알림 시각 최신순), 미읽음 수(수신 계정·상태), 거절 숨김의
 * 대상 찾기(수신 계정·작업)를 인덱스로 받친다.
 * 알림 시각은 감사 컬럼({@code created_at})이 아니라 알림을 일으킨 일이 생긴 시각이다. 읽음 처리와 거절 숨김이 같은 행을 동시에 고쳐도 서로의 변경을 덮어쓰지
 * 않도록 바뀐 컬럼만 UPDATE한다.
 */
@Entity
@DynamicUpdate
@Table(
        name = "notifications",
        indexes = {
            @Index(name = "ix_notifications_member_notified", columnList = "member_id, notified_at"),
            @Index(name = "ix_notifications_member_status", columnList = "member_id, status"),
            @Index(name = "ix_notifications_member_work", columnList = "member_id, work_id")
        })
class NotificationJpaEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long recipientAccountId;

    @Column(name = "company_id", nullable = false)
    private Long organizationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 40)
    private NotificationType type;

    @Column(name = "work_id")
    private Long workId;

    @Column(name = "message", nullable = false, length = Notification.MAX_MESSAGE_LENGTH)
    private String message;

    @Column(name = "notified_at", nullable = false)
    private Instant notifiedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private NotificationStatus status;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "is_hidden", nullable = false)
    private boolean hidden;

    protected NotificationJpaEntity() {}

    private NotificationJpaEntity(Notification notification) {
        this.recipientAccountId = notification.recipientAccountId();
        this.organizationId = notification.organizationId();
        this.type = notification.type();
        this.workId = notification.workId().orElse(null);
        this.message = notification.message();
        this.notifiedAt = notification.notifiedAt();
        update(notification);
    }

    static NotificationJpaEntity from(Notification notification) {
        if (notification.id().isPresent()) {
            throw new IllegalArgumentException("only new notifications can be saved");
        }
        return new NotificationJpaEntity(notification);
    }

    /** 수신 계정·종류·문구·알림 시각은 바뀌지 않으므로 읽음·숨김 상태만 옮긴다. */
    void update(Notification notification) {
        this.status = notification.status();
        this.readAt = notification.readAt().orElse(null);
        this.hidden = notification.isHidden();
    }

    Notification toDomain() {
        return Notification.reconstitute(
                id, recipientAccountId, organizationId, type, workId, message, notifiedAt, status, readAt, hidden);
    }
}
