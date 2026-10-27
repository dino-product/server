package com.orbit.organization.application.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.organization.application.port.in.query.ListPendingParticipationRequestsUseCase;
import com.orbit.organization.application.port.in.query.dto.ListPendingParticipationRequestsQuery;
import com.orbit.organization.application.port.in.query.dto.PendingParticipationRequestInfo;
import com.orbit.organization.application.port.out.AccountNamesPort;
import com.orbit.organization.application.port.out.MembershipRepository;
import com.orbit.organization.application.port.out.ParticipationRequestQueryPort;
import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.ParticipationRequest;

/** 그 발주사의 활성 총관리자에게 승인 대기 요청을 신청자 이름과 함께 보여 준다. 처리·취소된 요청은 보이지 않는다. */
@Service
public class ListPendingParticipationRequestsService implements ListPendingParticipationRequestsUseCase {

    private final MembershipRepository membershipRepository;
    private final ParticipationRequestQueryPort queryPort;
    private final AccountNamesPort accountNames;

    public ListPendingParticipationRequestsService(
            MembershipRepository membershipRepository,
            ParticipationRequestQueryPort queryPort,
            AccountNamesPort accountNames) {
        this.membershipRepository = membershipRepository;
        this.queryPort = queryPort;
        this.accountNames = accountNames;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PendingParticipationRequestInfo> list(ListPendingParticipationRequestsQuery query) {
        OrganizationId organizationId =
                OrganizationOwners.require(membershipRepository, query.accountId(), query.organizationId());
        List<ParticipationRequest> pending = queryPort.listPending(organizationId);
        if (pending.isEmpty()) {
            return List.of();
        }
        Map<AccountId, String> names = accountNames.findNames(
                pending.stream().map(ParticipationRequest::accountId).collect(Collectors.toSet()));
        return pending.stream()
                .map(request -> PendingParticipationRequestInfo.of(request, names.get(request.accountId())))
                .toList();
    }
}
