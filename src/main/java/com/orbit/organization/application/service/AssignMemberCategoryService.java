package com.orbit.organization.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.organization.application.error.OrganizationErrorCode;
import com.orbit.organization.application.port.in.command.AssignMemberCategoryUseCase;
import com.orbit.organization.application.port.in.command.dto.AssignMemberCategoryCommand;
import com.orbit.organization.application.port.out.MemberCategoryRepository;
import com.orbit.organization.application.port.out.MembershipRepository;
import com.orbit.organization.application.port.out.OrganizationRepository;
import com.orbit.organization.application.port.out.TechnicianRepository;
import com.orbit.organization.domain.MemberCategory;
import com.orbit.organization.domain.MemberCategoryId;
import com.orbit.organization.domain.MemberCategoryKind;
import com.orbit.organization.domain.Membership;
import com.orbit.organization.domain.MembershipId;
import com.orbit.organization.domain.MembershipStatus;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.Technician;
import com.orbit.organization.domain.TechnicianId;
import com.orbit.organization.domain.TechnicianStatus;
import com.orbit.shared.error.BusinessException;

/**
 * 총관리자가 직원 소속에는 직원 유형을, 기사 계약에는 기사 유형을 지정하거나 "유형 없음"으로 해제한다. 요청자 확인·발주사 잠금은 {@link OrganizationOwners}의
 * 총관리자 지정 순서를 따르고, 잠근 뒤 대상(없으면 404)·유형(삭제·다른 발주사·없음은 404 ORGANIZATION-008)·대상 상태(비활성이면 409
 * ORGANIZATION-021)를 확인한다. 유형 삭제도 발주사를 잠근 뒤 지정을 벌크 해제하므로, 잠근 뒤 유형을 확인해야 방금 삭제된 유형이 붙지 않는다. 같은 유형을 다시
 * 지정해도 성공한다.
 */
@Service
public class AssignMemberCategoryService implements AssignMemberCategoryUseCase {

    private final OrganizationRepository organizationRepository;
    private final MembershipRepository membershipRepository;
    private final TechnicianRepository technicianRepository;
    private final MemberCategoryRepository categoryRepository;

    public AssignMemberCategoryService(
            OrganizationRepository organizationRepository,
            MembershipRepository membershipRepository,
            TechnicianRepository technicianRepository,
            MemberCategoryRepository categoryRepository) {
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
        this.technicianRepository = technicianRepository;
        this.categoryRepository = categoryRepository;
    }

    @Override
    @Transactional
    public void assign(AssignMemberCategoryCommand command) {
        OrganizationId organizationId = OrganizationOwners.organizationId(command.organizationId());
        MemberCategoryKind kind = command.kind();
        MemberCategoryId categoryId = command.categoryId() == null ? null : MemberCategories.id(command.categoryId());
        if (kind == MemberCategoryKind.STAFF) {
            MembershipId membershipId = Participants.membershipId(command.memberId());
            lock(command, organizationId);
            Membership target = Participants.requireMembershipOf(membershipRepository, organizationId, membershipId);
            requireCategory(organizationId, kind, categoryId);
            requireActive(target.status() == MembershipStatus.ACTIVE);
            membershipRepository.changeStaffCategory(membershipId, categoryId);
        } else {
            TechnicianId technicianId = Participants.technicianId(command.memberId());
            lock(command, organizationId);
            Technician target = Participants.requireTechnicianOf(technicianRepository, organizationId, technicianId);
            requireCategory(organizationId, kind, categoryId);
            requireActive(target.status() == TechnicianStatus.ACTIVE);
            technicianRepository.changeTechnicianCategory(technicianId, categoryId);
        }
    }

    private void lock(AssignMemberCategoryCommand command, OrganizationId organizationId) {
        OrganizationOwners.lockAndRequireOwner(
                organizationRepository, membershipRepository, command.accountId(), organizationId);
    }

    private void requireCategory(OrganizationId organizationId, MemberCategoryKind kind, MemberCategoryId categoryId) {
        if (categoryId == null) {
            return;
        }
        MemberCategory category = MemberCategories.find(categoryRepository, organizationId, kind, categoryId);
        if (category.isDeleted()) {
            throw new BusinessException(OrganizationErrorCode.CATEGORY_NOT_FOUND);
        }
    }

    private static void requireActive(boolean active) {
        if (!active) {
            throw new BusinessException(OrganizationErrorCode.INACTIVE_MEMBER);
        }
    }
}
