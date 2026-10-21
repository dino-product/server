package com.orbit.organization.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.organization.StaffRegisteredWorks;
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
import com.orbit.organization.domain.MembershipStatus;
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
 *   <li>직원에서 기사로 바꿀 때 총관리자 소속(요청자 자신 포함)은 409(ORGANIZATION-025)로 거부한다. 그 직원이 등록한 작업 중 끝나지 않은 것이 있으면
 *       {@link StaffRegisteredWorks}로 확인해 409(ORGANIZATION-028)로 거부한다.
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
    private final StaffRegisteredWorks staffRegisteredWorks;
    private final Clock clock;

    public ChangeParticipantRoleService(
            OrganizationRepository organizationRepository,
            MembershipRepository membershipRepository,
            TechnicianRepository technicianRepository,
            MemberCategoryRepository categoryRepository,
            TechnicianWorkRelease technicianWorkRelease,
            StaffRegisteredWorks staffRegisteredWorks,
            Clock clock) {
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
        this.technicianRepository = technicianRepository;
        this.categoryRepository = categoryRepository;
        this.technicianWorkRelease = technicianWorkRelease;
        this.staffRegisteredWorks = staffRegisteredWorks;
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
        return toTechnician(command, organizationId, Participants.membershipId(command.memberId()), categoryId);
    }

    private ParticipantRoleChangeInfo toTechnician(
            ChangeParticipantRoleCommand command,
            OrganizationId organizationId,
            MembershipId membershipId,
            MemberCategoryId categoryId) {
        OrganizationOwners.lockAndRequireOwner(
                organizationRepository, membershipRepository, command.accountId(), organizationId);
        Membership target = Participants.requireMembershipOf(membershipRepository, organizationId, membershipId);
        if (target.isOwner()) {
            throw new BusinessException(OrganizationErrorCode.OWNER_ROLE_LOCKED);
        }
        requireActive(target.status() == MembershipStatus.ACTIVE);
        requireCategory(organizationId, MemberCategoryKind.TECHNICIAN, categoryId);
        if (staffRegisteredWorks.existsUnfinished(organizationId.value(), membershipId.value())) {
            throw new BusinessException(OrganizationErrorCode.STAFF_HAS_UNFINISHED_WORKS);
        }

        Instant now = clock.instant();
        // 총관리자는 위에서 거부했으므로 활성 총관리자 수는 판단에 쓰이지 않는다.
        membershipRepository.deactivate(target.deactivate(now, 0));
        Optional<Technician> previous = technicianRepository.find(organizationId, target.accountId());
        Technician technician = previous.isPresent()
                ? technicianRepository.reactivate(previous.get().reactivate(now), true)
                : technicianRepository.save(
                        Technician.join(organizationId, target.accountId(), target.joinedAt(), now));
        TechnicianId technicianId = technician.id().orElseThrow();
        if (categoryId != null) {
            technicianRepository.changeTechnicianCategory(technicianId, categoryId);
        }
        return new ParticipantRoleChangeInfo(ParticipationType.TECHNICIAN, technicianId.value(), List.of());
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
