package com.orbit.notification.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * 계정의 알림 수신 설정. 작업 배정·일정 변경·작업 취소 분류마다 푸시를 받을지 정하며, 처음에는 모두 켜져 있다. 끈 분류는 푸시만 보내지 않고 알림센터에는 남긴다.
 * 설정을 바꾸면 다음 알림부터 적용된다.
 */
public final class NotificationSetting {

    private final Long id;
    private final Long accountId;
    private boolean workAssignmentEnabled;
    private boolean scheduleChangeEnabled;
    private boolean workCancellationEnabled;

    private NotificationSetting(
            Long id,
            Long accountId,
            boolean workAssignmentEnabled,
            boolean scheduleChangeEnabled,
            boolean workCancellationEnabled) {
        if (id != null && id <= 0) {
            throw new IllegalArgumentException("id must be positive");
        }
        if (accountId == null || accountId <= 0) {
            throw new IllegalArgumentException("accountId must be positive");
        }
        this.id = id;
        this.accountId = accountId;
        this.workAssignmentEnabled = workAssignmentEnabled;
        this.scheduleChangeEnabled = scheduleChangeEnabled;
        this.workCancellationEnabled = workCancellationEnabled;
    }

    /** 아직 저장하지 않은 기본 설정(모두 켜짐). 설정을 바꾼 적 없는 계정은 이 값을 따른다. */
    public static NotificationSetting defaults(Long accountId) {
        return new NotificationSetting(null, accountId, true, true, true);
    }

    public static NotificationSetting reconstitute(
            Long id,
            Long accountId,
            boolean workAssignmentEnabled,
            boolean scheduleChangeEnabled,
            boolean workCancellationEnabled) {
        return new NotificationSetting(
                Objects.requireNonNull(id, "id must not be null"),
                accountId,
                workAssignmentEnabled,
                scheduleChangeEnabled,
                workCancellationEnabled);
    }

    /** 세 분류의 수신 여부를 한 번에 바꾼다. */
    public void change(boolean workAssignmentEnabled, boolean scheduleChangeEnabled, boolean workCancellationEnabled) {
        this.workAssignmentEnabled = workAssignmentEnabled;
        this.scheduleChangeEnabled = scheduleChangeEnabled;
        this.workCancellationEnabled = workCancellationEnabled;
    }

    /** 이 종류의 알림을 푸시할지. 푸시 대상이 아닌 종류는 false, 분류가 없는 기사 알림은 설정과 무관하게 true다. */
    public boolean allowsPush(NotificationType type) {
        if (!type.pushes()) {
            return false;
        }
        return type.category().map(this::isEnabled).orElse(true);
    }

    public boolean isEnabled(NotificationCategory category) {
        return switch (category) {
            case WORK_ASSIGNMENT -> workAssignmentEnabled;
            case SCHEDULE_CHANGE -> scheduleChangeEnabled;
            case WORK_CANCELLATION -> workCancellationEnabled;
        };
    }

    public Optional<Long> id() {
        return Optional.ofNullable(id);
    }

    public Long accountId() {
        return accountId;
    }

    public boolean workAssignmentEnabled() {
        return workAssignmentEnabled;
    }

    public boolean scheduleChangeEnabled() {
        return scheduleChangeEnabled;
    }

    public boolean workCancellationEnabled() {
        return workCancellationEnabled;
    }
}
