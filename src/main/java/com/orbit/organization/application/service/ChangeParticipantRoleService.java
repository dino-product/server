package com.orbit.organization.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.organization.TechnicianWorkRelease;
import com.orbit.organization.TechnicianWorkReleaseBusyException;
import com.orbit.organization.TechnicianWorkReleaseResult;
import com.orbit.organization.WorkReleaseBlocked;
import com.orbit.organization.WorkReleased;
import com.orbit.organization.application.error.OrganizationErrorCode;
import com.orbit.organization.application.port.in.command.ChangeParticipantRoleUseCase;
import com.orbit.organization.application.port.in.command.dto.ChangeParticipantRoleCommand;
import com.orbit.organization.application.port.in.command.dto.ParticipantRoleChangeInfo;
import com.orbit.organization.application.port.out.MemberCategoryRepository;
import com.orbit.organization.application.port.out.MembershipRepository;
import com.orbit.organization.application.port.out.OrganizationRepository;
import com.orbit.organization.application.port.out.TechnicianRepository;
import com.orbit.organization.domain.MemberCategory;
import com.orbit.organization.domain.MemberCategoryId;
import com.orbit.organization.domain.MemberCategoryKind;
import com.orbit.organization.domain.Membership;
import com.orbit.organization.domain.MembershipId;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.ParticipationType;
import com.orbit.organization.domain.Technician;
import com.orbit.organization.domain.TechnicianId;
import com.orbit.organization.domain.TechnicianStatus;
import com.orbit.shared.error.BusinessException;

/**
 * 총관리자가 참여 인원의 역할을 직원↔기사로 바꾼다([조직·계정] §4.1). 식별자 형식은 권한보다 먼저 400으로 거부하고, 요청자 확인·발주사 잠금은
 * {@link OrganizationOwners}의 총관리자 지정 순서를 따른 뒤 대상(없거나 다른 발주사면 404)·대상 상태(비활성이면 409 ORGANIZATION-026)·새 역할의
 * 유형(삭제·다른 발주사·없음은 404 ORGANIZATION-008)을 확인한다.
 *
 * <ul>
 *   <li>기존 행은 비활성으로 끝내고 대상 역할의 행은 없으면 이전 참여일로 만들고 있으면 되살린다. 기사 계약 ID가 유지되어 schedule 이력이 이어진다. 새 역할의
 *       세부 유형은 고른 유형으로 두고, 고르지 않았으면 "유형 없음"이다(되살린 행의 예전 유형도 해제한다).
 *   <li>기사에서 직원으로 바꾸면 같은 트랜잭션에서 {@link TechnicianWorkRelease}로 예정 작업을 대기함으로 돌린다. 작업중 작업이 있으면 아무것도 바꾸지
 *       않고 409(ORGANIZATION-027)로, 기사 일정 대기 초과는 409(ORGANIZATION-024)로 거부하며 그 밖의 예외는 그대로 전파한다.
 * </ul>
 */
@Service
public class ChangeParticipantRoleService implements ChangeParticipantRoleUseCase {

    private final OrganizationRepository organizationRepository;
    private final MembershipRepository membershipRepository;
    private final TechnicianRepository technicianRepository;
    private final MemberCategoryRepository categoryRepository;
    private final TechnicianWorkRelease technicianWorkRelease;
    private final Clock clock;

    public ChangeParticipantRoleService(
            OrganizationRepository organizationRepository,
            MembershipRepository membershipRepository,
            TechnicianRepository technicianRepository,
            MemberCategoryRepository categoryRepository,
            TechnicianWorkRelease technicianWorkRelease,
            Clock clock) {
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
        this.technicianRepository = technicianRepository;
        this.categoryRepository = categoryRepository;
        this.technicianWorkRelease = technicianWorkRelease;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ParticipantRoleChangeInfo changeRole(ChangeParticipantRoleCommand command) {
        OrganizationId organizationId = OrganizationOwners.organizationId(command.organizationId());
        MemberCategoryId categoryId = command.categoryId() == null ? null : MemberCategories.id(command.categoryId());
        if (command.type() == ParticipationType.TECHNICIAN) {
            return toStaff(command, organizationId, Participants.technicianId(command.memberId()), categoryId);
        }
        throw new UnsupportedOperationException("staff to technician is not implemented");
    }

    private ParticipantRoleChangeInfo toStaff(
            ChangeParticipantRoleCommand command,
            OrganizationId organizationId,
            TechnicianId technicianId,
            MemberCategoryId categoryId) {
        Membership requester = OrganizationOwners.lockAndRequireOwner(
                organizationRepository, membershipRepository, command.accountId(), organizationId);
        Technician target = Participants.requireTechnicianOf(technicianRepository, organizationId, technicianId);
        requireActive(target.status() == TechnicianStatus.ACTIVE);
        requireCategory(organizationId, MemberCategoryKind.STAFF, categoryId);
        List<Long> releasedWorkIds = releaseWorks(organizationId, technicianId, requester);

        Instant now = clock.instant();
        technicianRepository.deactivate(target.deactivate(now));
        Optional<Membership> previous = membershipRepository.find(organizationId, target.accountId());
        Membership staff = previous.isPresent()
                ? membershipRepository.reactivate(previous.get().reactivate(now), true)
                : membershipRepository.save(
                        Membership.join(organizationId, target.accountId(), target.joinedAt(), now));
        MembershipId membershipId = staff.id().orElseThrow();
        if (categoryId != null) {
            membershipRepository.changeStaffCategory(membershipId, categoryId);
        }
        return new ParticipantRoleChangeInfo(ParticipationType.STAFF, membershipId.value(), releasedWorkIds);
    }

    private List<Long> releaseWorks(OrganizationId organizationId, TechnicianId technicianId, Membership requester) {
        TechnicianWorkReleaseResult result;
        try {
            result = technicianWorkRelease.releaseForRoleChange(
                    organizationId.value(),
                    technicianId.value(),
                    requester.id().orElseThrow().value());
        } catch (TechnicianWorkReleaseBusyException exception) {
            throw new BusinessException(OrganizationErrorCode.TECHNICIAN_SCHEDULE_BUSY, exception);
        }
        return switch (result) {
            case WorkReleased released -> released.releasedWorkIds();
            case WorkReleaseBlocked blocked ->
                throw new BusinessException(OrganizationErrorCode.TECHNICIAN_WORK_IN_PROGRESS);
        };
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
            throw new BusinessException(OrganizationErrorCode.INACTIVE_MEMBER_ROLE);
        }
    }
}
