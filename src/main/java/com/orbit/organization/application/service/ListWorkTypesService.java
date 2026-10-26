package com.orbit.organization.application.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.organization.application.port.in.query.ListWorkTypesUseCase;
import com.orbit.organization.application.port.in.query.dto.ListWorkTypesQuery;
import com.orbit.organization.application.port.in.query.dto.WorkTypeInfo;
import com.orbit.organization.application.port.out.MembershipRepository;
import com.orbit.organization.application.port.out.WorkTypeRepository;
import com.orbit.organization.domain.OrganizationId;

/**
 * 삭제되지 않은 작업 유형 목록. 작업 등록·필터 선택지로 쓰므로 활성 직원 소속(총관리자 포함)이면 조회할 수 있고, 기사는 조회할 수 없다([조직·계정] §7·§10).
 */
@Service
public class ListWorkTypesService implements ListWorkTypesUseCase {

    private final MembershipRepository membershipRepository;
    private final WorkTypeRepository workTypeRepository;

    public ListWorkTypesService(MembershipRepository membershipRepository, WorkTypeRepository workTypeRepository) {
        this.membershipRepository = membershipRepository;
        this.workTypeRepository = workTypeRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkTypeInfo> list(ListWorkTypesQuery query) {
        OrganizationId organizationId =
                OrganizationOwners.requireMember(membershipRepository, query.accountId(), query.organizationId());
        return workTypeRepository.listUndeleted(organizationId).stream()
                .map(WorkTypeInfo::from)
                .toList();
    }
}
