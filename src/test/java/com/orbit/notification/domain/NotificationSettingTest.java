package com.orbit.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("알림 설정")
class NotificationSettingTest {

    @Test
    @DisplayName("기본 설정은 세 분류 모두 켜져 있어 기사 알림을 모두 푸시한다")
    void defaultsAllowEveryTechnicianPush() {
        NotificationSetting setting = NotificationSetting.defaults(7L);

        assertThat(setting.id()).isEmpty();
        assertThat(setting.workAssignmentEnabled()).isTrue();
        assertThat(setting.scheduleChangeEnabled()).isTrue();
        assertThat(setting.workCancellationEnabled()).isTrue();
        for (NotificationType type : NotificationType.values()) {
            assertThat(setting.allowsPush(type)).isEqualTo(type.pushes());
        }
    }

    @Test
    @DisplayName("끈 분류의 알림만 푸시하지 않고 일정 변경을 끄면 다른 기사에게 넘어감도 푸시하지 않는다")
    void blocksPushOnlyForDisabledCategory() {
        NotificationSetting setting = NotificationSetting.defaults(7L);

        setting.change(true, false, true);

        assertThat(setting.allowsPush(NotificationType.WORK_ASSIGNED)).isTrue();
        assertThat(setting.allowsPush(NotificationType.WORK_RESCHEDULED)).isFalse();
        assertThat(setting.allowsPush(NotificationType.WORK_REASSIGNED_AWAY)).isFalse();
        assertThat(setting.allowsPush(NotificationType.WORK_CANCELLED)).isTrue();
    }

    @Test
    @DisplayName("완료보고 요청은 모두 꺼도 푸시한다")
    void alwaysPushesCompletionReportRequest() {
        NotificationSetting setting = NotificationSetting.defaults(7L);

        setting.change(false, false, false);

        assertThat(setting.allowsPush(NotificationType.COMPLETION_REPORT_REQUESTED))
                .isTrue();
        assertThat(setting.allowsPush(NotificationType.WORK_ASSIGNED)).isFalse();
        assertThat(setting.allowsPush(NotificationType.WORK_CANCELLED)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(
            value = NotificationType.class,
            names = {"WORK_ACCEPTED", "WORK_REJECTED", "COMPLETION_REPORT_SUBMITTED"})
    @DisplayName("사무실 알림은 설정과 무관하게 푸시하지 않는다")
    void neverPushesOfficeNotifications(NotificationType type) {
        assertThat(type.audience()).isEqualTo(NotificationAudience.OFFICE);
        assertThat(type.category()).isEmpty();
        assertThat(NotificationSetting.defaults(7L).allowsPush(type)).isFalse();
    }
}
