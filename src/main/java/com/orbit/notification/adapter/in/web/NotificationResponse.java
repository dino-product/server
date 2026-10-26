package com.orbit.notification.adapter.in.web;

import java.time.Instant;

import com.orbit.notification.application.port.in.query.dto.NotificationInfo;
import com.orbit.notification.domain.NotificationType;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "알림센터의 알림 한 줄")
public record NotificationResponse(
        @Schema(description = "알림 식별자", example = "31") Long id,

        @Schema(
                description = "알림 종류. 기사 알림: WORK_ASSIGNED(작업 배정), WORK_RESCHEDULED(일정 변경), "
                        + "WORK_REASSIGNED_AWAY(다른 기사에게 넘어감), WORK_CANCELED(작업 취소), "
                        + "COMPLETION_REPORT_REQUESTED(완료보고 요청). 사무실 알림: WORK_ACCEPTED(수락), WORK_REJECTED(거절), "
                        + "COMPLETION_REPORT_SUBMITTED(완료보고 제출)",
                example = "WORK_ASSIGNED")
        NotificationType type,

        @Schema(description = "알림이 생긴 발주사 식별자", example = "1")
        Long organizationId,

        @Schema(
                description = "관련 작업 식별자. 눌렀을 때 이동할 작업 상세. 작업과 무관한 알림이면 null. "
                        + "다른 기사에게 넘어간 작업처럼 지금 볼 수 없는 작업이면 작업 상세가 SCHEDULE-001(404)이다",
                example = "10")
        Long workId,

        @Schema(description = "알림 문구. 시각은 한국 시간으로 쓴다", example = "10월 21일 09:00 [보일러 점검] 작업이 배정되었습니다.")
        String message,

        @Schema(description = "읽었는지", example = "false") boolean read,

        @Schema(description = "알림 시각(알림을 일으킨 일이 생긴 시각, UTC)", example = "2026-10-19T03:00:00Z")
        Instant createdAt) {

    static NotificationResponse from(NotificationInfo info) {
        return new NotificationResponse(
                info.id(),
                info.type(),
                info.organizationId(),
                info.workId(),
                info.message(),
                info.read(),
                info.notifiedAt());
    }
}
