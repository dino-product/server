package com.orbit.notification.application.port.in.query.dto;

import com.orbit.notification.domain.NotificationSetting;

public record NotificationSettingInfo(
        boolean workAssignmentEnabled, boolean scheduleChangeEnabled, boolean workCancellationEnabled) {

    public static NotificationSettingInfo from(NotificationSetting setting) {
        return new NotificationSettingInfo(
                setting.workAssignmentEnabled(), setting.scheduleChangeEnabled(), setting.workCancellationEnabled());
    }
}
