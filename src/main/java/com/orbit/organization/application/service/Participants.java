package com.orbit.organization.application.service;

import java.util.Optional;

import com.orbit.organization.application.error.OrganizationErrorCode;
import com.orbit.organization.application.port.out.MembershipRepository;
import com.orbit.organization.application.port.out.TechnicianRepository;
import com.orbit.organization.domain.Membership;
import com.orbit.organization.domain.MembershipId;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.ParticipationType;
import com.orbit.organization.domain.Technician;
import com.orbit.organization.domain.TechnicianId;
import com.orbit.shared.error.BusinessException;
import com.orbit.shared.error.CommonErrorCode;

/**
 * 참여 인원 변경(세부 유형 지정, 비활성화·재활성화)과 재참여 승인이 함께 쓰는 대상 확인·마지막 역할 판정. 식별자 형식은 권한보다 먼저 400(COMMON-400)으로 거부하고, 대상은 요청자 확인과 발주사
 * 잠금 뒤에 읽는다. 없거나 다른 발주사의 대상은 존재를 드러내지 않도록 404로 거부한다(직원 소속 ORGANIZATION-004, 기사 계약 ORGANIZATION-020).
 */
final class Participants {

    private Participants() {}

    static MembershipId membershipId(Long value) {
        try {
            return new MembershipId(value);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, exception);
        }
    }

    static TechnicianId technicianId(Long value) {
        try {
            return new TechnicianId(value);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, exception);
        }
    }

    static Membership requireMembershipOf(
            MembershipRepository repository, OrganizationId organizationId, MembershipId id) {
        return OrganizationOwners.requireMembershipOf(repository, organizationId, id);
    }

    static Technician requireTechnicianOf(
            TechnicianRepository repository, OrganizationId organizationId, TechnicianId id) {
        return repository
                .findById(id)
                .filter(technician -> technician.organizationId().equals(organizationId))
                .orElseThrow(() -> new BusinessException(OrganizationErrorCode.TECHNICIAN_NOT_FOUND));
    }

    /** 목록에 보이는 역할. 상태 변경 시각이 늦은 행이며, 같으면 직원 소속으로 본다. 둘 중 하나는 있어야 한다. */
    static ParticipationType lastRole(Optional<Membership> staff, Optional<Technician> technician) {
        if (staff.isEmpty()) {
            return ParticipationType.TECHNICIAN;
        }
        if (technician.isEmpty()) {
            return ParticipationType.STAFF;
        }
        return technician.get().statusChangedAt().isAfter(staff.get().statusChangedAt())
                ? ParticipationType.TECHNICIAN
                : ParticipationType.STAFF;
    }
}
