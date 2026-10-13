package com.orbit.organization.adapter.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.MembershipId;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.ParticipationRequest;
import com.orbit.organization.domain.ParticipationRequestId;
import com.orbit.organization.domain.ParticipationRequestStatus;
import com.orbit.organization.domain.ParticipationType;
import com.orbit.shared.persistence.BaseTimeEntity;

/**
 * 참여 요청. 발주사·계정은 다른 애그리게잇·모듈이므로 ID로만 둔다. 재요청은 새 행이므로 같은 계정·발주사의 행이 여럿일 수 있고, 승인 대기 행이 하나뿐인지는 참여
 * 요청 서비스가 발주사 행 잠금 안에서 확인한다. 처리한 총관리자는 직원 소속 ID로 둔다.
 */
@Entity
@Table(
        name = "participation_requests",
        indexes = {
            @Index(name = "idx_participation_requests_member", columnList = "member_id"),
            @Index(name = "idx_participation_requests_company_status", columnList = "company_id, status")
        })
class ParticipationRequestJpaEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long organizationId;

    @Column(name = "member_id", nullable = false)
    private Long accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "desired_type", nullable = false, length = 16)
    private ParticipationType desiredType;

    @Enumerated(EnumType.STRING)
    @Column(name = "confirmed_type", length = 16)
    private ParticipationType confirmedType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ParticipationRequestStatus status;

    @Column(name = "reject_reason", length = 255)
    private String rejectReason;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decided_by_membership_id")
    private Long decidedByMembershipId;

    protected ParticipationRequestJpaEntity() {}

    private ParticipationRequestJpaEntity(ParticipationRequest request) {
        this.organizationId = request.organizationId().value();
        this.accountId = request.accountId().value();
        this.desiredType = request.desiredType();
        this.requestedAt = request.requestedAt();
        update(request);
    }

    static ParticipationRequestJpaEntity from(ParticipationRequest request) {
        if (request.id().isPresent()) {
            throw new IllegalArgumentException("only new participation requests can be saved");
        }
        return new ParticipationRequestJpaEntity(request);
    }

    /** 요청자·발주사·희망 유형·요청 시각은 바뀌지 않으므로 상태와 처리 결과만 옮긴다. */
    void update(ParticipationRequest request) {
        this.confirmedType = request.confirmedType().orElse(null);
        this.status = request.status();
        this.rejectReason = request.rejectReason().orElse(null);
        this.decidedAt = request.decidedAt().orElse(null);
        this.decidedByMembershipId =
                request.decidedBy().map(MembershipId::value).orElse(null);
    }

    ParticipationRequest toDomain() {
        return ParticipationRequest.reconstitute(
                new ParticipationRequestId(id),
                new OrganizationId(organizationId),
                new AccountId(accountId),
                desiredType,
                confirmedType,
                status,
                rejectReason,
                requestedAt,
                decidedAt,
                decidedByMembershipId == null ? null : new MembershipId(decidedByMembershipId));
    }
}
