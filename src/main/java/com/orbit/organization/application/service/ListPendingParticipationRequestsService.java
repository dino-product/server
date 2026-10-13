package com.orbit.organization.application.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.organization.application.port.in.query.ListPendingParticipationRequestsUseCase;
import com.orbit.organization.application.port.in.query.dto.ListPendingParticipationRequestsQuery;
import com.orbit.organization.application.port.in.query.dto.PendingParticipationRequestInfo;
import com.orbit.organization.application.port.out.MembershipRepository;
import com.orbit.organization.application.port.out.ParticipationRequestQueryPort;
import com.orbit.organization.domain.OrganizationId;

/** 그 발주사의 활성 총관리자에게 승인 대기 요청을 보여 준다. 처리·취소된 요청은 보이지 않는다. */
@Service
public class ListPendingParticipationRequestsService implements ListPendingParticipationRequestsUseCase {

    private final MembershipRepository membershipRepository;
    private final ParticipationRequestQueryPort queryPort;

    public ListPendingParticipationRequestsService(
            MembershipRepository membershipRepository, ParticipationRequestQueryPort queryPort) {
        this.membershipRepository = membershipRepository;
        this.queryPort = queryPort;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PendingParticipationRequestInfo> list(ListPendingParticipationRequestsQuery query) {
        OrganizationId organizationId =
                OrganizationOwners.require(membershipRepository, query.accountId(), query.organizationId());
        return queryPort.listPending(organizationId).stream()
                .map(PendingParticipationRequestInfo::from)
                .toList();
    }
}
