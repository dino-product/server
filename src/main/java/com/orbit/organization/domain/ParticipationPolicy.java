package com.orbit.organization.domain;

import java.util.List;
import java.util.Objects;

/**
 * 같은 회사·계정의 참여 중복 정책. 호출자는 비활성 관계를 포함한 최신 조회 결과를 전달하고,
 * 검사와 생성 사이의 동시성을 저장소 트랜잭션에서 제어해야 한다.
 */
public final class ParticipationPolicy {
    private ParticipationPolicy() {}

    /** 직원 소속과 기사 계약 중 어느 것을 생성하든 동일하게 적용한다. 기존 관계는 재생성하지 않는다. */
    public static void requireCanCreateRelationship(
            OrganizationId organizationId,
            AuthAccountId authAccountId,
            List<Membership> memberships,
            List<Technician> technicians) {
        if (organizationId == null || authAccountId == null) {
            throw new OrganizationRuleViolation("organization and account must not be null");
        }
        requireEvidence(memberships);
        requireEvidence(technicians);
        boolean hasMembership = memberships.stream()
                .anyMatch(membership -> organizationId.equals(membership.organizationId())
                        && authAccountId.equals(membership.authAccountId()));
        boolean hasContract = technicians.stream()
                .anyMatch(technician -> organizationId.equals(technician.organizationId())
                        && authAccountId.equals(technician.authAccountId()));
        if (hasMembership || hasContract) {
            throw new OrganizationRuleViolation("account already has a relationship with this organization");
        }
    }

    public static void requireCanRequest(
            OrganizationId organizationId,
            AuthAccountId authAccountId,
            List<Membership> memberships,
            List<Technician> technicians,
            List<ParticipantRequest> requests) {
        requireCanCreateRelationship(organizationId, authAccountId, memberships, technicians);
        requireEvidence(requests);
        boolean pending = requests.stream()
                .anyMatch(request -> organizationId.equals(request.organizationId())
                        && authAccountId.equals(request.authAccountId())
                        && request.status() == ParticipantRequestStatus.PENDING);
        if (pending) {
            throw new OrganizationRuleViolation("account already has a pending request for this organization");
        }
    }

    private static void requireEvidence(List<?> evidence) {
        if (evidence == null || evidence.stream().anyMatch(Objects::isNull)) {
            throw new OrganizationRuleViolation("relationship evidence must not contain null");
        }
    }
}
