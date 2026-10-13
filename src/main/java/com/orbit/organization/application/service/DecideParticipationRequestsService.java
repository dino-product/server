package com.orbit.organization.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.organization.application.error.OrganizationErrorCode;
import com.orbit.organization.application.port.in.command.ApproveParticipationRequestsUseCase;
import com.orbit.organization.application.port.in.command.RejectParticipationRequestsUseCase;
import com.orbit.organization.application.port.in.command.dto.ApproveParticipationRequestsCommand;
import com.orbit.organization.application.port.in.command.dto.ApproveParticipationRequestsCommand.Approval;
import com.orbit.organization.application.port.in.command.dto.ParticipationRequestDecisionInfo;
import com.orbit.organization.application.port.in.command.dto.ParticipationRequestDecisionOutcome;
import com.orbit.organization.application.port.in.command.dto.RejectParticipationRequestsCommand;
import com.orbit.organization.application.port.out.AccountMembershipQueryPort;
import com.orbit.organization.application.port.out.MembershipRepository;
import com.orbit.organization.application.port.out.ParticipationRequestRepository;
import com.orbit.organization.application.port.out.TechnicianRepository;
import com.orbit.organization.domain.Membership;
import com.orbit.organization.domain.MembershipId;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.ParticipationRequest;
import com.orbit.organization.domain.ParticipationRequestId;
import com.orbit.organization.domain.ParticipationType;
import com.orbit.organization.domain.RejectReason;
import com.orbit.organization.domain.Technician;
import com.orbit.shared.error.BusinessException;
import com.orbit.shared.error.CommonErrorCode;

/**
 * 총관리자가 참여 요청을 행별 또는 여러 건 한 번에 승인·거절한다. 한 건만 처리해도 같은 경로를 쓴다.
 *
 * <ul>
 *   <li>목록 모양(빈 목록·한도 초과·중복·0 이하 식별자·확정 유형 누락)은 권한보다 먼저 400(COMMON-400)으로 거부한다. 그다음 활성 총관리자인지,
 *       거절 사유가 규칙에 맞는지(ORGANIZATION-019) 확인한 뒤에야 요청 행을 잠근다.
 *   <li>요청 행은 식별자 오름차순으로 잠가 읽는다. 겹치는 요청을 동시에 처리하는 총관리자들이 같은 순서로 기다려 교착이 생기지 않고, 늦은 쪽은 앞 처리가
 *       커밋한 상태를 보고 그 행을 실패로 돌려준다(먼저 처리된 결과만 반영). 요청자 취소도 같은 행 잠금으로 줄을 선다.
 *   <li>처리할 수 없는 행(없음·다른 발주사·이미 처리·취소·이미 소속 행이 있음)은 건너뛰고 결과에 실패로 남기며, 나머지 행은 같은 트랜잭션에서 처리한다.
 *   <li>승인하면 같은 트랜잭션에서 확정 유형의 활성 직원 소속 또는 기사 계약을 만든다. 신청자에게 그 발주사의 행이 이미 있으면 재활성화하지 않는다(O-15).
 * </ul>
 */
@Service
public class DecideParticipationRequestsService
        implements ApproveParticipationRequestsUseCase, RejectParticipationRequestsUseCase {

    /** 한 번에 처리할 수 있는 요청 수. 화면에서 고를 수 있는 승인 대기 건수보다 넉넉하게 둔 방어 한도다. */
    static final int MAX_BATCH_SIZE = 100;

    private final MembershipRepository membershipRepository;
    private final ParticipationRequestRepository requestRepository;
    private final TechnicianRepository technicianRepository;
    private final AccountMembershipQueryPort membershipQueryPort;
    private final Clock clock;

    public DecideParticipationRequestsService(
            MembershipRepository membershipRepository,
            ParticipationRequestRepository requestRepository,
            TechnicianRepository technicianRepository,
            AccountMembershipQueryPort membershipQueryPort,
            Clock clock) {
        this.membershipRepository = membershipRepository;
        this.requestRepository = requestRepository;
        this.technicianRepository = technicianRepository;
        this.membershipQueryPort = membershipQueryPort;
        this.clock = clock;
    }

    @Override
    @Transactional
    public List<ParticipationRequestDecisionInfo> approve(ApproveParticipationRequestsCommand command) {
        List<Approval> approvals = Objects.requireNonNull(command.approvals(), "approvals must not be null");
        if (approvals.stream().anyMatch(approval -> approval.confirmedType() == null)) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST);
        }
        List<ParticipationRequestId> ids =
                requestIds(approvals.stream().map(Approval::requestId).toList());
        Map<ParticipationRequestId, ParticipationType> confirmedTypes = new HashMap<>();
        for (int index = 0; index < ids.size(); index++) {
            confirmedTypes.put(ids.get(index), approvals.get(index).confirmedType());
        }
        Membership decider =
                OrganizationOwners.requireOwner(membershipRepository, command.accountId(), command.organizationId());
        Instant now = clock.instant();
        return decide(
                ids,
                decider,
                request -> approve(request, confirmedTypes.get(request.id().orElseThrow()), decider, now));
    }

    @Override
    @Transactional
    public List<ParticipationRequestDecisionInfo> reject(RejectParticipationRequestsCommand command) {
        List<ParticipationRequestId> ids =
                requestIds(Objects.requireNonNull(command.requestIds(), "requestIds must not be null"));
        Membership decider =
                OrganizationOwners.requireOwner(membershipRepository, command.accountId(), command.organizationId());
        Optional<RejectReason> reason = rejectReason(command.reason());
        Instant now = clock.instant();
        return decide(ids, decider, request -> {
            request.reject(reason, deciderId(decider), now);
            return ParticipationRequestDecisionOutcome.REJECTED;
        });
    }

    /** 요청을 오름차순으로 잠가 처리하고, 결과는 입력 순서대로 돌려준다. */
    private List<ParticipationRequestDecisionInfo> decide(
            List<ParticipationRequestId> ids,
            Membership decider,
            Function<ParticipationRequest, ParticipationRequestDecisionOutcome> decision) {
        OrganizationId organizationId = decider.organizationId();
        Map<ParticipationRequestId, ParticipationRequestDecisionInfo> results = new HashMap<>();
        for (ParticipationRequestId id : ids.stream()
                .sorted(Comparator.comparing(ParticipationRequestId::value))
                .toList()) {
            results.put(id, decideOne(id, organizationId, decision));
        }
        return ids.stream().map(results::get).toList();
    }

    private ParticipationRequestDecisionInfo decideOne(
            ParticipationRequestId id,
            OrganizationId organizationId,
            Function<ParticipationRequest, ParticipationRequestDecisionOutcome> decision) {
        Optional<ParticipationRequest> found = requestRepository
                .findByIdForUpdate(id)
                .filter(request -> request.organizationId().equals(organizationId));
        if (found.isEmpty()) {
            return new ParticipationRequestDecisionInfo(
                    id.value(), ParticipationRequestDecisionOutcome.NOT_FOUND, null);
        }
        ParticipationRequest request = found.get();
        if (!request.isPending()) {
            return result(request, ParticipationRequestDecisionOutcome.NOT_PENDING);
        }
        ParticipationRequestDecisionOutcome outcome = decision.apply(request);
        if (request.isPending()) {
            return result(request, outcome);
        }
        return result(requestRepository.save(request), outcome);
    }

    private ParticipationRequestDecisionOutcome approve(
            ParticipationRequest request, ParticipationType confirmedType, Membership decider, Instant now) {
        boolean hasRow = membershipQueryPort.listMemberships(request.accountId()).stream()
                .anyMatch(membership -> membership.organizationId().equals(request.organizationId()));
        if (hasRow) {
            return ParticipationRequestDecisionOutcome.ALREADY_MEMBER;
        }
        request.approve(confirmedType, deciderId(decider), now);
        if (confirmedType == ParticipationType.STAFF) {
            membershipRepository.save(Membership.join(request.organizationId(), request.accountId(), now));
        } else {
            technicianRepository.save(Technician.join(request.organizationId(), request.accountId(), now));
        }
        return ParticipationRequestDecisionOutcome.APPROVED;
    }

    private static ParticipationRequestDecisionInfo result(
            ParticipationRequest request, ParticipationRequestDecisionOutcome outcome) {
        return new ParticipationRequestDecisionInfo(request.id().orElseThrow().value(), outcome, request.status());
    }

    private static MembershipId deciderId(Membership decider) {
        return decider.id().orElseThrow(() -> new IllegalStateException("decider membership must be saved"));
    }

    private static List<ParticipationRequestId> requestIds(List<Long> values) {
        if (values.isEmpty() || values.size() > MAX_BATCH_SIZE || new HashSet<>(values).size() != values.size()) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST);
        }
        try {
            return values.stream().map(ParticipationRequestId::new).toList();
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, exception);
        }
    }

    private static Optional<RejectReason> rejectReason(String raw) {
        try {
            return RejectReason.from(raw);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(OrganizationErrorCode.INVALID_REJECT_REASON, exception);
        }
    }
}
