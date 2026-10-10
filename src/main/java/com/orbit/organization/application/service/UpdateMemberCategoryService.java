package com.orbit.organization.application.service;

import java.time.Clock;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.organization.application.error.OrganizationErrorCode;
import com.orbit.organization.application.port.in.command.UpdateMemberCategoryUseCase;
import com.orbit.organization.application.port.in.command.dto.UpdateMemberCategoryCommand;
import com.orbit.organization.application.port.in.query.dto.MemberCategoryInfo;
import com.orbit.organization.application.port.out.MemberCategoryRepository;
import com.orbit.organization.application.port.out.MembershipRepository;
import com.orbit.organization.application.port.out.OrganizationRepository;
import com.orbit.organization.domain.CategoryColor;
import com.orbit.organization.domain.CategoryName;
import com.orbit.organization.domain.MemberCategory;
import com.orbit.organization.domain.MemberCategoryId;
import com.orbit.organization.domain.MemberCategoryKind;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.shared.error.BusinessException;

/**
 * 직원·기사 유형의 이름·색상 수정. 삭제된 유형은 목록에서 빠진 유형이므로 없는 유형과 같이 거부한다. 발주사 행을 잠가 이름 중복 확인과 삭제와의 엇갈림을 막는다.
 * 바뀐 이름·색상은 유형을 ID로 참조하는 모든 소속 표시에 그대로 반영된다.
 */
@Service
public class UpdateMemberCategoryService implements UpdateMemberCategoryUseCase {

    private final OrganizationRepository organizationRepository;
    private final MembershipRepository membershipRepository;
    private final MemberCategoryRepository categoryRepository;
    private final Clock clock;

    public UpdateMemberCategoryService(
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
    public MemberCategoryInfo update(UpdateMemberCategoryCommand command) {
        MemberCategoryKind kind = Objects.requireNonNull(command.kind(), "kind must not be null");
        Objects.requireNonNull(command.accountId(), "accountId must not be null");
        MemberCategoryId categoryId = MemberCategories.id(command.categoryId());
        OrganizationId organizationId =
                OrganizationOwners.require(membershipRepository, command.accountId(), command.organizationId());
        CategoryName name = MemberCategories.name(command.name());
        CategoryColor color = MemberCategories.color(command.color());

        organizationRepository.lock(organizationId);
        MemberCategory category = MemberCategories.find(categoryRepository, organizationId, kind, categoryId);
        if (category.isDeleted()) {
            throw new BusinessException(OrganizationErrorCode.CATEGORY_NOT_FOUND);
        }
        MemberCategories.requireUniqueName(categoryRepository, organizationId, kind, name, categoryId);
        category.change(name, color, clock.instant());
        return MemberCategoryInfo.from(MemberCategories.save(categoryRepository, category));
    }
}
