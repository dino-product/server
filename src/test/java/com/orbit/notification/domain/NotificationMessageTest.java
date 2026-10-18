package com.orbit.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("알림 문구")
class NotificationMessageTest {

    private static final Instant START = Instant.parse("2026-10-21T00:00:00Z");

    @Test
    @DisplayName("배정 문구는 예정 시작 시각을 한국 시간으로 앞에 쓴다")
    void composesAssignedMessageWithSeoulTime() {
        assertThat(NotificationMessage.compose(NotificationType.WORK_ASSIGNED, "보일러 점검", START, null, null))
                .isEqualTo("10월 21일 09:00 [보일러 점검] 작업이 배정되었습니다.");
    }

    @Test
    @DisplayName("일정 변경 문구는 바뀐 시각을 한국 시간으로 쓰고 날짜가 바뀌는 시각도 한국 날짜로 쓴다")
    void composesRescheduledMessageWithSeoulDate() {
        assertThat(NotificationMessage.compose(
                        NotificationType.WORK_RESCHEDULED, "배관 설치", Instant.parse("2026-03-04T23:05:00Z"), null, null))
                .isEqualTo("[배관 설치] 작업 시간이 3월 5일 08:05으로 변경되었습니다.");
    }

    @Test
    @DisplayName("시각이 없는 종류의 문구를 만든다")
    void composesMessagesWithoutTime() {
        assertThat(compose(NotificationType.WORK_REASSIGNED_AWAY)).isEqualTo("[보일러 점검] 작업이 다른 기사에게 넘어갔습니다.");
        assertThat(compose(NotificationType.WORK_CANCELLED)).isEqualTo("[보일러 점검] 작업이 취소되었습니다.");
        assertThat(compose(NotificationType.COMPLETION_REPORT_REQUESTED)).isEqualTo("[보일러 점검] 작업의 완료보고를 올려 주세요.");
        assertThat(compose(NotificationType.WORK_ACCEPTED)).isEqualTo("담당 기사가 [보일러 점검] 작업을 수락했습니다.");
        assertThat(compose(NotificationType.COMPLETION_REPORT_SUBMITTED)).isEqualTo("[보일러 점검] 완료 보고가 제출되었습니다.");
    }

    @ParameterizedTest
    @CsvSource({
        "SCHEDULE_CONFLICT, 일정이 맞지 않습니다",
        "ALREADY_ASSIGNED, 이미 다른 작업이 있습니다",
        "LOCATION_TOO_FAR, 작업 위치가 너무 멉니다",
        "SCOPE_MISMATCH, 작업 내용이 맞지 않습니다"
    })
    @DisplayName("거절 문구는 선택한 사유 코드를 거절 화면의 선택지 문구로 쓴다")
    void composesRejectedMessageWithReasonLabel(String code, String label) {
        assertThat(rejected(code, null)).isEqualTo("담당 기사가 [보일러 점검] 작업을 거절했습니다. 사유: " + label);
    }

    @Test
    @DisplayName("기타 사유는 기사가 적은 메모를 쓰고 메모가 없으면 기타로 쓴다")
    void composesOtherReasonWithNote() {
        assertThat(rejected("OTHER", "자재가 없습니다")).endsWith("사유: 자재가 없습니다");
        assertThat(rejected("OTHER", null)).endsWith("사유: 기타");
        assertThat(rejected("OTHER", " ")).endsWith("사유: 기타");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "NEW_REASON")
    @DisplayName("모르는 사유 코드는 코드를 그대로 보이지 않고 메모나 기타로 쓴다")
    void neverShowsUnknownReasonCode(String code) {
        assertThat(rejected(code, "자재가 없습니다")).endsWith("사유: 자재가 없습니다");
        assertThat(rejected(code, null)).endsWith("사유: 기타").doesNotContain("NEW_REASON");
    }

    @ParameterizedTest
    @EnumSource(
            value = NotificationType.class,
            names = {"WORK_ASSIGNED", "WORK_RESCHEDULED"})
    @DisplayName("배정·일정 변경은 예정 시작 시각이 없으면 만들지 않는다")
    void requiresStartTimeForTimedMessages(NotificationType type) {
        assertThatThrownBy(() -> compose(type)).isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    @DisplayName("작업명이 비어 있으면 만들지 않는다")
    void rejectsBlankWorkName(String blank) {
        assertThatThrownBy(() -> NotificationMessage.compose(NotificationType.WORK_CANCELLED, blank, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("가장 긴 작업명과 메모의 거절 문구도 알림 문구 길이 안에 든다")
    void longestRejectedMessageFitsMessageLength() {
        String message = NotificationMessage.compose(
                NotificationType.WORK_REJECTED, "가".repeat(100), null, "OTHER", "나".repeat(255));

        assertThat(message.length()).isLessThanOrEqualTo(Notification.MAX_MESSAGE_LENGTH);
    }

    private static String compose(NotificationType type) {
        return NotificationMessage.compose(type, "보일러 점검", null, null, null);
    }

    private static String rejected(String code, String note) {
        return NotificationMessage.compose(NotificationType.WORK_REJECTED, "보일러 점검", null, code, note);
    }
}
