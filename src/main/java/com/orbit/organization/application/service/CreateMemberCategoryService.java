package com.orbit.organization.application.service;

import java.time.Clock;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.organization.application.port.in.command.CreateMemberCategoryUseCase;
import com.orbit.organization.application.port.in.command.dto.CreateMemberCategoryCommand;
import com.orbit.organization.application.port.in.query.dto.MemberCategoryInfo;
import com.orbit.organization.application.port.out.MemberCategoryRepository;
import com.orbit.organization.application.port.out.MembershipRepository;
import com.orbit.organization.application.port.out.OrganizationRepository;
import com.orbit.organization.domain.CategoryColor;
import com.orbit.organization.domain.CategoryName;
import com.orbit.organization.domain.MemberCategory;
import com.orbit.organization.domain.MemberCategoryKind;
import com.orbit.organization.domain.OrganizationId;

/** 직원·기사 유형 생성. 이름 중복 확인과 저장 사이의 경합을 막으려고 발주사 행을 잠근 뒤 확인한다. 삭제된 유형과 같은 이름은 새 유형으로 만든다. */
@Service
public class CreateMemberCategoryService implements CreateMemberCategoryUseCase {

    private final OrganizationRepository organizationRepository;
    private final MembershipRepository membershipRepository;
    private final MemberCategoryRepository categoryRepository;
    private final Clock clock;

    public CreateMemberCategoryService(
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
    public MemberCategoryInfo create(CreateMemberCategoryCommand command) {
        MemberCategoryKind kind = Objects.requireNonNull(command.kind(), "kind must not be null");
        OrganizationId organizationId =
                OrganizationOwners.require(membershipRepository, command.accountId(), command.organizationId());
        CategoryName name = MemberCategories.name(command.name());
        CategoryColor color = MemberCategories.color(command.color());

        organizationRepository.lock(organizationId);
        MemberCategories.requireUniqueName(categoryRepository, organizationId, kind, name, null);
        MemberCategory category = MemberCategory.create(organizationId, kind, name, color, clock.instant());
        return MemberCategoryInfo.from(MemberCategories.save(categoryRepository, category));
    }
}
