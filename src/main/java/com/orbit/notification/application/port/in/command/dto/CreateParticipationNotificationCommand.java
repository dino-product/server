package com.orbit.notification.application.port.in.command.dto;

import java.time.Instant;

import com.orbit.notification.domain.NotificationType;
import com.orbit.notification.domain.ParticipationType;

/**
 * 참여 요청 결과 알림 한 건의 재료. 받는 사람은 참여를 요청한 계정이다. 희망·확정 유형은 승인에만 쓰며 거절이면 null이다. 종류별 팩터리로 만든다.
 *
 * @param participationRequestId 결과가 난 참여 요청
 * @param applicantAccountId 참여를 요청한 계정
 * @param occurredAt 승인·거절 시각. 알림 시각으로 쓴다
 */
public record CreateParticipationNotificationCommand(
        NotificationType type,
        Long participationRequestId,
        Long organizationId,
        Long applicantAccountId,
        ParticipationType desiredType,
        ParticipationType confirmedType,
        Instant occurredAt) {

    public static CreateParticipationNotificationCommand approved(
            Long participationRequestId,
            Long organizationId,
            Long applicantAccountId,
            ParticipationType desiredType,
            ParticipationType confirmedType,
            Instant occurredAt) {
        return new CreateParticipationNotificationCommand(
                NotificationType.PARTICIPATION_REQUEST_APPROVED,
                participationRequestId,
                organizationId,
                applicantAccountId,
                desiredType,
                confirmedType,
                occurredAt);
    }

    public static CreateParticipationNotificationCommand rejected(
            Long participationRequestId, Long organizationId, Long applicantAccountId, Instant occurredAt) {
        return new CreateParticipationNotificationCommand(
                NotificationType.PARTICIPATION_REQUEST_REJECTED,
                participationRequestId,
                organizationId,
                applicantAccountId,
                null,
                null,
                occurredAt);
    }
}
