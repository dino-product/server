package com.orbit.organization.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.organization.application.port.in.query.GetCompanyCodeUseCase;
import com.orbit.organization.application.port.in.query.dto.CompanyCodeInfo;
import com.orbit.organization.application.port.in.query.dto.GetCompanyCodeQuery;
import com.orbit.organization.application.port.out.MembershipRepository;
import com.orbit.organization.application.port.out.OrganizationRepository;
import com.orbit.organization.domain.OrganizationId;

/** 회사 코드 관리 화면의 현재 회사 코드 조회. */
@Service
public class GetCompanyCodeService implements GetCompanyCodeUseCase {

    private final OrganizationRepository organizationRepository;
    private final MembershipRepository membershipRepository;

    public GetCompanyCodeService(
            OrganizationRepository organizationRepository, MembershipRepository membershipRepository) {
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public CompanyCodeInfo get(GetCompanyCodeQuery query) {
        OrganizationId organizationId =
                OrganizationOwners.require(membershipRepository, query.accountId(), query.organizationId());
        return organizationRepository
                .findById(organizationId)
                .map(CompanyCodeInfo::from)
                .orElseThrow(() -> new IllegalStateException("active membership must belong to an organization"));
    }
}
