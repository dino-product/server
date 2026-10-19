package com.orbit.organization.application.service;

import java.time.Clock;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.organization.TechnicianWorkRelease;
import com.orbit.organization.application.error.OrganizationErrorCode;
import com.orbit.organization.application.port.in.command.DeactivateParticipantUseCase;
import com.orbit.organization.application.port.in.command.ReactivateParticipantUseCase;
import com.orbit.organization.application.port.in.command.dto.ChangeParticipantStatusCommand;
import com.orbit.organization.application.port.out.MembershipRepository;
import com.orbit.organization.application.port.out.OrganizationRepository;
import com.orbit.organization.application.port.out.TechnicianRepository;
import com.orbit.organization.domain.LastOwnerException;
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
 * 총관리자가 직원 소속(총관리자 포함)·기사 계약을 비활성화하거나 다시 활성으로 바꾼다([조직·계정] §4.1). 요청자 확인·발주사 잠금은
 * {@link OrganizationOwners}의 총관리자 지정 순서를 따르고, 잠근 뒤 대상을 읽는다(없거나 다른 발주사면 404). 이미 그 상태면 저장하지 않고 성공한다.
 *
 * <ul>
 *   <li>비활성화는 상태만 바꾸고 세부 유형·참여일은 남긴다. 총관리자 소속은 총관리자 표시를 떼며, 활성 총관리자 수를 잠근 뒤 세어 마지막 1명이면 409
 *       (ORGANIZATION-005)로 거부한다. 요청자 자신도 대상이 될 수 있다.
 *   <li>기사 계약 비활성화는 같은 트랜잭션에서 {@link TechnicianWorkRelease}로 그 기사의 배정됨·작업전 작업을 대기함으로 돌린다([스케줄·배정] §9). 반환은
 *       요청자 소속을 처리자로 남기며, 반환이 실패하면 예외를 그대로 전파해 비활성화도 롤백한다.
 *   <li>재활성화는 같은 계정이 그 발주사에서 다른 역할로 활성이면 409(ORGANIZATION-022)로, 목록에 보이는 마지막 역할 행이 아니면(역할이 바뀌기 전 행)
 *       409(ORGANIZATION-023)로 거부한다. 역할은 재활성화로 바꾸지 않으므로 세부 유형은 유지한다.
 * </ul>
 */
@Service
public class ChangeParticipantStatusService implements DeactivateParticipantUseCase, ReactivateParticipantUseCase {

    private final OrganizationRepository organizationRepository;
    private final MembershipRepository membershipRepository;
    private final TechnicianRepository technicianRepository;
    private final TechnicianWorkRelease technicianWorkRelease;
    private final Clock clock;

    public ChangeParticipantStatusService(
            OrganizationRepository organizationRepository,
            MembershipRepository membershipRepository,
            TechnicianRepository technicianRepository,
            TechnicianWorkRelease technicianWorkRelease,
            Clock clock) {
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
        this.technicianRepository = technicianRepository;
        this.technicianWorkRelease = technicianWorkRelease;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void deactivate(ChangeParticipantStatusCommand command) {
        OrganizationId organizationId = OrganizationOwners.organizationId(command.organizationId());
        if (command.type() == ParticipationType.STAFF) {
            MembershipId membershipId = Participants.membershipId(command.memberId());
            lock(command, organizationId);
            Membership target = Participants.requireMembershipOf(membershipRepository, organizationId, membershipId);
            if (target.status() == MembershipStatus.ACTIVE) {
                membershipRepository.deactivate(
                        deactivated(target, membershipRepository.countActiveOwners(organizationId)));
            }
            return;
        }
        TechnicianId technicianId = Participants.technicianId(command.memberId());
        Membership requester = lock(command, organizationId);
        Technician target = Participants.requireTechnicianOf(technicianRepository, organizationId, technicianId);
        if (target.status() == TechnicianStatus.ACTIVE) {
            technicianWorkRelease.releaseForDeactivation(
                    organizationId.value(),
                    technicianId.value(),
                    requester.id().orElseThrow().value());
            technicianRepository.deactivate(target.deactivate(clock.instant()));
        }
    }

    @Override
    @Transactional
    public void reactivate(ChangeParticipantStatusCommand command) {
        OrganizationId organizationId = OrganizationOwners.organizationId(command.organizationId());
        if (command.type() == ParticipationType.STAFF) {
            MembershipId membershipId = Participants.membershipId(command.memberId());
            lock(command, organizationId);
            Membership target = Participants.requireMembershipOf(membershipRepository, organizationId, membershipId);
            if (target.status() == MembershipStatus.DEACTIVATED) {
                Optional<Technician> technician = technicianRepository.find(organizationId, target.accountId());
                requireNoOtherActiveRole(technician
                        .filter(other -> other.status() == TechnicianStatus.ACTIVE)
                        .isPresent());
                requireLastRole(Participants.lastRole(Optional.of(target), technician) == ParticipationType.STAFF);
                membershipRepository.reactivate(target.reactivate(clock.instant()), false);
            }
            return;
        }
        TechnicianId technicianId = Participants.technicianId(command.memberId());
        lock(command, organizationId);
        Technician target = Participants.requireTechnicianOf(technicianRepository, organizationId, technicianId);
        if (target.status() == TechnicianStatus.DEACTIVATED) {
            Optional<Membership> staff = membershipRepository.find(organizationId, target.accountId());
            requireNoOtherActiveRole(staff.filter(other -> other.status() == MembershipStatus.ACTIVE)
                    .isPresent());
            requireLastRole(Participants.lastRole(staff, Optional.of(target)) == ParticipationType.TECHNICIAN);
            technicianRepository.reactivate(target.reactivate(clock.instant()), false);
        }
    }

    private Membership lock(ChangeParticipantStatusCommand command, OrganizationId organizationId) {
        return OrganizationOwners.lockAndRequireOwner(
                organizationRepository, membershipRepository, command.accountId(), organizationId);
    }

    private Membership deactivated(Membership target, long activeOwners) {
        try {
            return target.deactivate(clock.instant(), activeOwners);
        } catch (LastOwnerException exception) {
            throw new BusinessException(OrganizationErrorCode.LAST_OWNER, exception);
        }
    }

    private static void requireNoOtherActiveRole(boolean activeInOtherRole) {
        if (activeInOtherRole) {
            throw new BusinessException(OrganizationErrorCode.ACTIVE_IN_OTHER_ROLE);
        }
    }

    private static void requireLastRole(boolean lastRole) {
        if (!lastRole) {
            throw new BusinessException(OrganizationErrorCode.NOT_LAST_ROLE);
        }
    }
}
