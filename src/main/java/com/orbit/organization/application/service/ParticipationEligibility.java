package com.orbit.organization.application.service;

import java.util.List;
import java.util.Optional;

import com.orbit.organization.application.port.in.query.dto.ParticipationAvailability;
import com.orbit.organization.application.port.out.AccountMembership;
import com.orbit.organization.application.port.out.AccountMembershipQueryPort;
import com.orbit.organization.application.port.out.ParticipationRequestRepository;
import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.ParticipationRequest;

/**
 * 계정이 발주사에 참여 요청을 보낼 수 있는지. 활성 소속을 먼저 보고 승인 대기 요청을 본다. 비활성 소속만 있으면 요청할 수 있다([조직·계정] §6).
 * 직원 소속·기사 계약은 역할 변경 전후의 두 행을 같은 시점으로 보도록 {@link AccountMembershipQueryPort}의 한 문장 조회로 읽는다.
 *
 * @param pendingRequest {@link ParticipationAvailability#PENDING_REQUEST}일 때의 그 요청
 */
record ParticipationEligibility(ParticipationAvailability availability, Optional<ParticipationRequest> pendingRequest) {

    static ParticipationEligibility evaluate(
            AccountMembershipQueryPort membershipQueryPort,
            ParticipationRequestRepository requestRepository,
            OrganizationId organizationId,
            AccountId accountId) {
        List<AccountMembership> memberships = membershipQueryPort.listMemberships(accountId).stream()
                .filter(membership -> membership.organizationId().equals(organizationId))
                .toList();
        if (memberships.stream().anyMatch(AccountMembership::active)) {
            return new ParticipationEligibility(ParticipationAvailability.ALREADY_MEMBER, Optional.empty());
        }
        Optional<ParticipationRequest> pending = requestRepository.findPending(organizationId, accountId);
        if (pending.isPresent()) {
            return new ParticipationEligibility(ParticipationAvailability.PENDING_REQUEST, pending);
        }
        return new ParticipationEligibility(ParticipationAvailability.AVAILABLE, Optional.empty());
    }
}
