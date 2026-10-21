package com.orbit.organization.application.port.in.command.dto;

import java.util.List;

import com.orbit.organization.domain.ParticipationType;

/**
 * 역할을 바꾼 결과. {@code type}은 바뀐 뒤 역할, {@code memberId}는 그 역할의 직원 소속 또는 기사 계약 식별자다. {@code releasedWorkIds}는 기사에서
 * 직원으로 바꾸며 대기함으로 돌린 작업이며, 없거나 직원에서 기사로 바꿨으면 비어 있다.
 */
public record ParticipantRoleChangeInfo(ParticipationType type, Long memberId, List<Long> releasedWorkIds) {

    public ParticipantRoleChangeInfo {
        releasedWorkIds = List.copyOf(releasedWorkIds);
    }
}
