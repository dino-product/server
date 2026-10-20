package com.orbit.notification.application.port.in.command.dto;

import java.time.Instant;

import com.orbit.notification.domain.NotificationType;

/**
 * 작업 알림 한 건의 재료. 받는 사람은 종류가 정한다. 기사 알림은 기사 계약({@code technicianId}), 사무실 알림은 작업을 등록한 직원 소속
 * ({@code registrarMembershipId})의 계정이다. 종류마다 쓰지 않는 값은 null(불리언은 false)이며, 종류별 팩터리로 만든다.
 *
 * @param technicianAccepted 받는 기사가 그 일 전에 작업을 수락한 상태였는지. 수락한 작업에만 보내는 종류(일정 변경·다른 기사에게 넘어감)만 본다
 * @param scheduledStartAt 배정·일정 변경의 예정 시작 시각
 * @param rejectionReasonCode 거절 사유 코드(schedule 거절 사유 이름)
 * @param rejectionReasonNote 기사가 적은 거절 메모. 없으면 null
 * @param occurredAt 작업에 그 일이 일어난 시각. 알림 시각으로 쓴다
 */
public record CreateWorkNotificationCommand(
        NotificationType type,
        Long organizationId,
        Long workId,
        String workName,
        Long technicianId,
        boolean technicianAccepted,
        Long registrarMembershipId,
        Instant scheduledStartAt,
        String rejectionReasonCode,
        String rejectionReasonNote,
        Instant occurredAt) {

    /** 기사에게 작업이 배정됐다(대기함 작업의 배정, 다른 기사로 바꾼 배정의 새 기사). */
    public static CreateWorkNotificationCommand workAssigned(
            Long organizationId,
            Long workId,
            String workName,
            Long technicianId,
            Instant scheduledStartAt,
            Instant occurredAt) {
        return toTechnician(
                NotificationType.WORK_ASSIGNED,
                organizationId,
                workId,
                workName,
                technicianId,
                false,
                scheduledStartAt,
                occurredAt);
    }

    /** 담당 기사는 그대로 시간만 바뀌었다. {@code accepted}는 기사가 수락한 작업이었는지다. */
    public static CreateWorkNotificationCommand workRescheduled(
            Long organizationId,
            Long workId,
            String workName,
            Long technicianId,
            boolean accepted,
            Instant scheduledStartAt,
            Instant occurredAt) {
        return toTechnician(
                NotificationType.WORK_RESCHEDULED,
                organizationId,
                workId,
                workName,
                technicianId,
                accepted,
                scheduledStartAt,
                occurredAt);
    }

    /** 작업이 이전 기사에게서 다른 기사로 넘어갔다. {@code previousAccepted}는 이전 기사가 수락한 작업이었는지다. */
    public static CreateWorkNotificationCommand workReassignedAway(
            Long organizationId,
            Long workId,
            String workName,
            Long previousTechnicianId,
            boolean previousAccepted,
            Instant occurredAt) {
        return toTechnician(
                NotificationType.WORK_REASSIGNED_AWAY,
                organizationId,
                workId,
                workName,
                previousTechnicianId,
                previousAccepted,
                null,
                occurredAt);
    }

    /** 작업이 취소됐다. 담당 기사가 없던 작업이면 {@code technicianId}가 null이고 알림을 만들지 않는다. */
    public static CreateWorkNotificationCommand workCancelled(
            Long organizationId, Long workId, String workName, Long technicianId, Instant occurredAt) {
        return toTechnician(
                NotificationType.WORK_CANCELED,
                organizationId,
                workId,
                workName,
                technicianId,
                false,
                null,
                occurredAt);
    }

    public static CreateWorkNotificationCommand completionReportRequested(
            Long organizationId, Long workId, String workName, Long technicianId, Instant occurredAt) {
        return toTechnician(
                NotificationType.COMPLETION_REPORT_REQUESTED,
                organizationId,
                workId,
                workName,
                technicianId,
                false,
                null,
                occurredAt);
    }

    public static CreateWorkNotificationCommand workAccepted(
            Long organizationId,
            Long workId,
            String workName,
            Long registrarMembershipId,
            Long technicianId,
            Instant occurredAt) {
        return toRegistrar(
                NotificationType.WORK_ACCEPTED,
                organizationId,
                workId,
                workName,
                registrarMembershipId,
                technicianId,
                null,
                null,
                occurredAt);
    }

    /** 기사가 거절했다. {@code technicianId}는 거절한 기사이며 그 기사가 이 작업에 대해 받은 알림을 숨기는 데 쓴다. */
    public static CreateWorkNotificationCommand workRejected(
            Long organizationId,
            Long workId,
            String workName,
            Long registrarMembershipId,
            Long technicianId,
            String reasonCode,
            String reasonNote,
            Instant occurredAt) {
        return toRegistrar(
                NotificationType.WORK_REJECTED,
                organizationId,
                workId,
                workName,
                registrarMembershipId,
                technicianId,
                reasonCode,
                reasonNote,
                occurredAt);
    }

    public static CreateWorkNotificationCommand completionReportSubmitted(
            Long organizationId,
            Long workId,
            String workName,
            Long registrarMembershipId,
            Long technicianId,
            Instant occurredAt) {
        return toRegistrar(
                NotificationType.COMPLETION_REPORT_SUBMITTED,
                organizationId,
                workId,
                workName,
                registrarMembershipId,
                technicianId,
                null,
                null,
                occurredAt);
    }

    private static CreateWorkNotificationCommand toTechnician(
            NotificationType type,
            Long organizationId,
            Long workId,
            String workName,
            Long technicianId,
            boolean technicianAccepted,
            Instant scheduledStartAt,
            Instant occurredAt) {
        return new CreateWorkNotificationCommand(
                type,
                organizationId,
                workId,
                workName,
                technicianId,
                technicianAccepted,
                null,
                scheduledStartAt,
                null,
                null,
                occurredAt);
    }

    private static CreateWorkNotificationCommand toRegistrar(
            NotificationType type,
            Long organizationId,
            Long workId,
            String workName,
            Long registrarMembershipId,
            Long technicianId,
            String reasonCode,
            String reasonNote,
            Instant occurredAt) {
        return new CreateWorkNotificationCommand(
                type,
                organizationId,
                workId,
                workName,
                technicianId,
                false,
                registrarMembershipId,
                null,
                reasonCode,
                reasonNote,
                occurredAt);
    }
}
