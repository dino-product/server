package com.orbit.organization.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.organization.application.port.in.query.GetOrganizationUseCase;
import com.orbit.organization.application.port.in.query.dto.GetOrganizationQuery;
import com.orbit.organization.application.port.in.query.dto.OrganizationInfo;
import com.orbit.organization.application.port.out.MembershipRepository;
import com.orbit.organization.application.port.out.OrganizationRepository;
import com.orbit.organization.domain.OrganizationId;

/** 조직 설정 화면의 발주사 정보 조회. 수정 폼을 채우는 용도라 수정과 같은 총관리자 권한을 쓴다. */
@Service
public class GetOrganizationService implements GetOrganizationUseCase {

    private final OrganizationRepository organizationRepository;
    private final MembershipRepository membershipRepository;

    public GetOrganizationService(
            OrganizationRepository organizationRepository, MembershipRepository membershipRepository) {
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public OrganizationInfo get(GetOrganizationQuery query) {
        OrganizationId organizationId =
                OrganizationOwners.require(membershipRepository, query.accountId(), query.organizationId());
        return organizationRepository
                .findById(organizationId)
                .map(OrganizationInfo::from)
                .orElseThrow(() -> new IllegalStateException("active membership must belong to an organization"));
    }
}
