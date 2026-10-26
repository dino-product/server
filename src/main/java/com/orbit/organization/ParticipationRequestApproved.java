package com.orbit.organization;

import java.time.Instant;

/**
 * 총관리자가 참여 요청을 승인해 신청자가 발주사의 직원 또는 기사가 되었다. 승인과 같은 트랜잭션에서 발행되며, 소비 모듈은 커밋 뒤에 받는다. 확정 유형이 희망 유형과
 * 다를 수 있다.
 *
 * @param accountId 신청자 계정
 * @param occurredAt 승인 시각
 */
public record ParticipationRequestApproved(
        Long requestId, Long organizationId, Long accountId, Type desiredType, Type confirmedType, Instant occurredAt) {

    /** 참여 유형. STAFF 직원, TECHNICIAN 기사. */
    public enum Type {
        STAFF,
        TECHNICIAN
    }
}
