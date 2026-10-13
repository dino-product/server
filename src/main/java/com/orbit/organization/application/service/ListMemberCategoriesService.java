package com.orbit.organization.application.service;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.organization.application.port.in.query.ListMemberCategoriesUseCase;
import com.orbit.organization.application.port.in.query.dto.ListMemberCategoriesQuery;
import com.orbit.organization.application.port.in.query.dto.MemberCategoryInfo;
import com.orbit.organization.application.port.out.MemberCategoryRepository;
import com.orbit.organization.application.port.out.MembershipRepository;
import com.orbit.organization.domain.MemberCategoryKind;
import com.orbit.organization.domain.OrganizationId;

/**
 * 삭제되지 않은 직원·기사 유형 목록. 타임테이블 필터처럼 총관리자가 아닌 직원도 유형을 고르므로 활성 직원 소속(총관리자 포함)이면 조회할 수 있고, 기사는 조회할 수
 * 없다(조회 권한·목록 순서는 기획 확정 전 잠정 — O-17·O-18).
 */
@Service
public class ListMemberCategoriesService implements ListMemberCategoriesUseCase {

    private final MembershipRepository membershipRepository;
    private final MemberCategoryRepository categoryRepository;

    public ListMemberCategoriesService(
            MembershipRepository membershipRepository, MemberCategoryRepository categoryRepository) {
        this.membershipRepository = membershipRepository;
        this.categoryRepository = categoryRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<MemberCategoryInfo> list(ListMemberCategoriesQuery query) {
        MemberCategoryKind kind = Objects.requireNonNull(query.kind(), "kind must not be null");
        OrganizationId organizationId =
                OrganizationOwners.requireMember(membershipRepository, query.accountId(), query.organizationId());
        return categoryRepository.listUndeleted(organizationId, kind).stream()
                .map(MemberCategoryInfo::from)
                .toList();
    }
}
