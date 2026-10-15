package com.orbit.schedule.application.port.in.command.dto;

/**
 * 기사 계약이 끝날 때 그 기사의 예정 작업을 대기함으로 돌리는 요청. organization이 기사 계약(technicianId)을 끝내는 트랜잭션 안에서 보내며, 처리한 관리자는
 * 조직 소속(processedByMembershipId)이다.
 */
public record ReleaseTechnicianWorksCommand(
        Long organizationId, Long technicianId, Long processedByMembershipId, Cause cause) {

    /** 기사 계약이 끝난 까닭. 역할 변경은 작업중 작업이 있으면 막히고, 비활성화는 막히지 않는다. */
    public enum Cause {
        ROLE_CHANGE,
        DEACTIVATION
    }
}
