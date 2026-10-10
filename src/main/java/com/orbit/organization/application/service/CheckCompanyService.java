package com.orbit.organization.application.service;

import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.organization.application.port.in.query.CheckCompanyUseCase;
import com.orbit.organization.application.port.in.query.dto.CheckCompanyQuery;
import com.orbit.organization.application.port.in.query.dto.CompanyCheckInfo;
import com.orbit.organization.application.port.out.AccountMembershipQueryPort;
import com.orbit.organization.application.port.out.OrganizationRepository;
import com.orbit.organization.application.port.out.ParticipationRequestRepository;
import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.Organization;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.ParticipationRequest;
import com.orbit.organization.domain.ParticipationRequestId;

/** 소속이 없어도 인증된 계정이면 누구나 확인할 수 있다. 확인 결과는 참여 요청 때 다시 판정하므로 요청을 보장하지 않는다. */
@Service
public class CheckCompanyService implements CheckCompanyUseCase {

    private final OrganizationRepository organizationRepository;
    private final AccountMembershipQueryPort membershipQueryPort;
    private final ParticipationRequestRepository requestRepository;

    public CheckCompanyService(
            OrganizationRepository organizationRepository,
            AccountMembershipQueryPort membershipQueryPort,
            ParticipationRequestRepository requestRepository) {
        this.organizationRepository = organizationRepository;
        this.membershipQueryPort = membershipQueryPort;
        this.requestRepository = requestRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public CompanyCheckInfo check(CheckCompanyQuery query) {
        Organization organization = CompanyCodes.resolve(organizationRepository, query.companyCode());
        OrganizationId organizationId = organization.id().orElseThrow();
        ParticipationEligibility eligibility = ParticipationEligibility.evaluate(
                membershipQueryPort,
                requestRepository,
                organizationId,
                new AccountId(Objects.requireNonNull(query.accountId(), "accountId must not be null")));
        return new CompanyCheckInfo(
                organizationId.value(),
                organization.name().value(),
                eligibility.availability(),
                eligibility
                        .pendingRequest()
                        .flatMap(ParticipationRequest::id)
                        .map(ParticipationRequestId::value)
                        .orElse(null));
    }
}
