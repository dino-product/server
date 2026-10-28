package com.orbit.notification.domain;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Objects;

/** 알림 종류별 문구. 작업명은 대괄호로 감싸고, 시각은 한국 시간으로 "M월 d일 HH:mm"처럼 쓴다. 참여 요청 결과 문구에는 발주사명을 붙이지 않는다. */
public final class NotificationMessage {

    /** 문구의 시각을 표시하는 시간대. */
    public static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Seoul");

    private static final DateTimeFormatter DISPLAY_TIME =
            DateTimeFormatter.ofPattern("M'월' d'일' HH:mm").withZone(DISPLAY_ZONE);

    /**
     * 거절 사유 코드(schedule {@code WorkRejected}의 거절 사유 이름)별 표시 문구. 기사 앱 거절 화면의 선택지(Figma 135:1753, [현장 수행] §4
     * FLD-005)를 따른다. 여기 없는 코드(기타 {@code OTHER}, schedule이 새로 더한 사유)는 메모로 쓴다.
     */
    private static final Map<String, String> REJECTION_REASONS = Map.of(
            "SCHEDULE_CONFLICT", "일정이 맞지 않습니다",
            "ALREADY_ASSIGNED", "이미 다른 작업이 있습니다",
            "LOCATION_TOO_FAR", "작업 위치가 너무 멉니다",
            "SCOPE_MISMATCH", "작업 내용이 맞지 않습니다");

    private static final String UNSPECIFIED_REJECTION_REASON = "기타";

    private static final String PARTICIPATION_APPROVED = "참여 요청이 승인되었습니다.";

    private static final String PARTICIPATION_REJECTED = "참여 요청이 거절되었습니다.";

    private NotificationMessage() {}

    /**
     * 작업 알림 종류에 맞는 문구를 만든다. 참여 요청 결과는 {@link #composeParticipationApproved}·
     * {@link #composeParticipationRejected}로 만든다. 거절 문구의 사유는 아는 코드면 선택지 문구, 기타나 모르는 코드면 기사가 적은
     * 메모(없으면 "기타")다. 모르는 코드를 그대로 쓰지 않아 내부 이름이 사용자에게 보이지 않는다.
     *
     * @param workName 작업명. 비어 있지 않다
     * @param scheduledStartAt 예정 시작 시각. 배정·일정 변경에만 쓰며 둘에는 필수다
     * @param rejectionReasonCode 거절 사유 코드. 거절에만 쓴다
     * @param rejectionReasonNote 기사가 적은 거절 메모. 거절에만 쓰며 없으면 null이다
     */
    public static String compose(
            NotificationType type,
            String workName,
            Instant scheduledStartAt,
            String rejectionReasonCode,
            String rejectionReasonNote) {
        Objects.requireNonNull(type, "type must not be null");
        String work = "[" + requireText(workName, "workName") + "]";
        return switch (type) {
            case WORK_ASSIGNED -> displayTime(scheduledStartAt) + " " + work + " 작업이 배정되었습니다.";
            case WORK_RESCHEDULED -> work + " 작업 시간이 " + displayTime(scheduledStartAt) + "으로 변경되었습니다.";
            case WORK_REASSIGNED_AWAY -> work + " 작업이 다른 기사에게 넘어갔습니다.";
            case WORK_CANCELED -> work + " 작업이 취소되었습니다.";
            case COMPLETION_REPORT_REQUESTED -> work + " 작업의 완료보고를 올려 주세요.";
            case WORK_ACCEPTED -> "담당 기사가 " + work + " 작업을 수락했습니다.";
            case WORK_REJECTED ->
                "담당 기사가 " + work + " 작업을 거절했습니다. 사유: " + rejectionReason(rejectionReasonCode, rejectionReasonNote);
            case COMPLETION_REPORT_SUBMITTED -> work + " 완료 보고가 제출되었습니다.";
            case PARTICIPATION_REQUEST_APPROVED, PARTICIPATION_REQUEST_REJECTED ->
                throw new IllegalArgumentException(type + " is not a work notification");
        };
    }

    /** 참여 요청 승인 문구. 확정 유형이 희망 유형과 다르면 "참여 유형: 희망 → 확정"을 덧붙인다. */
    public static String composeParticipationApproved(ParticipationType desiredType, ParticipationType confirmedType) {
        Objects.requireNonNull(desiredType, "desiredType must not be null");
        Objects.requireNonNull(confirmedType, "confirmedType must not be null");
        if (desiredType == confirmedType) {
            return PARTICIPATION_APPROVED;
        }
        return PARTICIPATION_APPROVED + " 참여 유형: " + desiredType.label() + " → " + confirmedType.label();
    }

    /** 참여 요청 거절 문구. 총관리자가 입력한 거절 사유가 있으면 "사유: …"를 덧붙인다. */
    public static String composeParticipationRejected(String rejectReason) {
        if (rejectReason == null || rejectReason.isBlank()) {
            return PARTICIPATION_REJECTED;
        }
        return PARTICIPATION_REJECTED + " 사유: " + rejectReason;
    }

    private static String rejectionReason(String code, String note) {
        String label = code == null ? null : REJECTION_REASONS.get(code);
        if (label != null) {
            return label;
        }
        return note == null || note.isBlank() ? UNSPECIFIED_REJECTION_REASON : note;
    }

    private static String displayTime(Instant at) {
        return DISPLAY_TIME.format(Objects.requireNonNull(at, "scheduledStartAt must not be null"));
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
