package com.orbit.notification.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import com.orbit.notification.domain.NotificationSetting;
import com.orbit.shared.persistence.BaseTimeEntity;

/** 계정별 알림 설정. 계정은 auth 모듈의 식별자라 ID로만 두고, 계정마다 한 행이다. 바꾼 적 없는 계정은 행이 없고 기본값(모두 켜짐)을 따른다. */
@Entity
@Table(
        name = "notification_settings",
        uniqueConstraints =
                @UniqueConstraint(name = NotificationSettingJpaEntity.MEMBER_UNIQUE, columnNames = "member_id"))
class NotificationSettingJpaEntity extends BaseTimeEntity {

    static final String MEMBER_UNIQUE = "uk_notification_settings_member";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long accountId;

    @Column(name = "work_assignment_enabled", nullable = false)
    private boolean workAssignmentEnabled;

    @Column(name = "schedule_change_enabled", nullable = false)
    private boolean scheduleChangeEnabled;

    @Column(name = "work_cancellation_enabled", nullable = false)
    private boolean workCancellationEnabled;

    protected NotificationSettingJpaEntity() {}

    private NotificationSettingJpaEntity(NotificationSetting setting) {
        this.accountId = setting.accountId();
        update(setting);
    }

    static NotificationSettingJpaEntity from(NotificationSetting setting) {
        if (setting.id().isPresent()) {
            throw new IllegalArgumentException("only new notification settings can be saved");
        }
        return new NotificationSettingJpaEntity(setting);
    }

    /** 계정은 바뀌지 않으므로 수신 여부만 옮긴다. */
    void update(NotificationSetting setting) {
        this.workAssignmentEnabled = setting.workAssignmentEnabled();
        this.scheduleChangeEnabled = setting.scheduleChangeEnabled();
        this.workCancellationEnabled = setting.workCancellationEnabled();
    }

    NotificationSetting toDomain() {
        return NotificationSetting.reconstitute(
                id, accountId, workAssignmentEnabled, scheduleChangeEnabled, workCancellationEnabled);
    }
}
