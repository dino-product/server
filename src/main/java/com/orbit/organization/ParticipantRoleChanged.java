package com.orbit.organization;

import java.time.Instant;
import java.util.List;

/**
 * 총관리자가 참여 인원의 역할을 직원↔기사로 바꿨다. 역할 변경과 같은 트랜잭션에서 발행되며, 소비 모듈은 커밋 뒤에 받는다. 당사자 알림은 바뀐 역할 기준으로 보낸다.
 *
 * @param organizationId 발주사
 * @param accountId 역할이 바뀐 계정
 * @param previousRole 바뀌기 전 역할
 * @param newRole 바뀐 뒤 역할
 * @param memberId 바뀐 역할의 식별자. {@code newRole}이 직원이면 직원 소속, 기사면 기사 계약 식별자다
 * @param releasedWorkIds 기사에서 직원으로 바꾸며 대기함으로 돌린 작업. 없으면 비어 있다
 * @param occurredAt 역할 변경 시각
 */
public record ParticipantRoleChanged(
        Long organizationId,
        Long accountId,
        Role previousRole,
        Role newRole,
        Long memberId,
        List<Long> releasedWorkIds,
        Instant occurredAt) {

    public ParticipantRoleChanged {
        releasedWorkIds = List.copyOf(releasedWorkIds);
    }

    /** 역할. STAFF 직원, TECHNICIAN 기사. */
    public enum Role {
        STAFF,
        TECHNICIAN
    }
}
