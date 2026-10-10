package com.orbit.organization.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.organization.application.port.in.command.DeleteMemberCategoryUseCase;
import com.orbit.organization.application.port.in.command.dto.DeleteMemberCategoryCommand;
import com.orbit.organization.application.port.out.MemberCategoryRepository;
import com.orbit.organization.application.port.out.MembershipRepository;
import com.orbit.organization.application.port.out.OrganizationRepository;
import com.orbit.organization.domain.MemberCategory;
import com.orbit.organization.domain.MemberCategoryId;
import com.orbit.organization.domain.MemberCategoryKind;
import com.orbit.organization.domain.OrganizationId;

/**
 * 직원·기사 유형 소프트 삭제. 이미 삭제된 유형은 아무것도 바꾸지 않고 성공한다. 직원 유형을 지운 같은 트랜잭션에서 그 유형이 지정된 직원 소속을 "유형 없음"으로
 * 바꾼다. 기사 유형이 지정된 기사 계약의 해제는 기사 계약이 생길 때 같은 방식으로 붙인다. 삭제 직전 이름을 남길지는 보류(O-33)라 남기지 않는다.
 */
@Service
public class DeleteMemberCategoryService implements DeleteMemberCategoryUseCase {

    private final OrganizationRepository organizationRepository;
    private final MembershipRepository membershipRepository;
    private final MemberCategoryRepository categoryRepository;
    private final Clock clock;

    public DeleteMemberCategoryService(
            OrganizationRepository organizationRepository,
            MembershipRepository membershipRepository,
            MemberCategoryRepository categoryRepository,
            Clock clock) {
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
        this.categoryRepository = categoryRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void delete(DeleteMemberCategoryCommand command) {
        MemberCategoryKind kind = Objects.requireNonNull(command.kind(), "kind must not be null");
        Objects.requireNonNull(command.accountId(), "accountId must not be null");
        MemberCategoryId categoryId = MemberCategories.id(command.categoryId());
        OrganizationId organizationId =
                OrganizationOwners.require(membershipRepository, command.accountId(), command.organizationId());

        OrganizationLocks.lock(organizationRepository, organizationId);
        MemberCategory category = MemberCategories.find(categoryRepository, organizationId, kind, categoryId);
        Instant now = clock.instant();
        if (!category.delete(now)) {
            return;
        }
        categoryRepository.save(category);
        if (kind == MemberCategoryKind.STAFF) {
            membershipRepository.unassignStaffCategory(organizationId, categoryId, now);
        }
    }
}
