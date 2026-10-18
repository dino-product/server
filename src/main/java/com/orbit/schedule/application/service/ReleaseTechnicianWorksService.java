package com.orbit.schedule.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.orbit.schedule.application.port.in.command.ReleaseTechnicianWorksUseCase;
import com.orbit.schedule.application.port.in.command.dto.ReleaseTechnicianWorksCommand;
import com.orbit.schedule.application.port.in.command.dto.ReleaseTechnicianWorksCommand.Cause;
import com.orbit.schedule.application.port.in.command.dto.TechnicianWorkReleaseInfo;
import com.orbit.schedule.application.port.out.LockTechnicianSchedulePort;
import com.orbit.schedule.application.port.out.WorkRepository;
import com.orbit.schedule.domain.MembershipId;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.TechnicianId;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkStatus;

/**
 * 기사 계약이 역할 변경·비활성화로 끝날 때 그 기사의 배정됨·작업전 작업을 대기함으로 돌린다. 반환은 배정 해제와 같아 처리한 관리자가 배정의 종료 처리자로 남는다.
 * 역할 변경은 작업중 작업이 하나라도 있으면 아무것도 바꾸지 않고 그 작업을 돌려주며, 비활성화는 작업중·완료 작업을 그대로 둔다. 반환할 때 대기함 작업이 미리
 * 입력한 담당기사가 이 기사이면 그 담당기사도 비운다([스케줄·배정] §9). 이 작업은 배정이 풀린 것이 아니므로 돌려주는 반환 목록에 넣지 않는다. 모든 작업을 바꾼 뒤에 저장한다.
 * 한 작업이라도 돌릴 수 없으면(배정 이력 시각 역전 등) 관리자의 입력 오류가 아니므로 오류 코드로 바꾸지 않고 도메인 예외를 그대로 전파해 500으로 드러내며, 호출자의
 * 변경까지 롤백된다.
 *
 * <p>organization의 트랜잭션 안에서만 실행해 기사 계약 종료와 함께 커밋·롤백된다(바깥 트랜잭션이 없으면 Spring이 거부한다). 배정·재배정과 같은 기사 잠금을 잡은
 * 뒤 기사의 활성 작업과 미리 입력한 대기함 작업을 읽어, 잠금을 기다리는 동안 커밋된 배정·등록까지 처리한다. 잠근 뒤의 조회가 앞선 커밋을 보려면 READ COMMITTED여야 하는데, 바깥 트랜잭션에
 * 참여하므로 격리 수준을 지정할 수 없다. 그래서 바깥 트랜잭션이 그보다 높은 격리 수준을 지정했으면 잠그기 전에 거부하고, 지정하지 않았으면 PostgreSQL 기본값
 * READ COMMITTED에 기댄다.
 */
@Service
public class ReleaseTechnicianWorksService implements ReleaseTechnicianWorksUseCase {

    private final WorkRepository workRepository;
    private final LockTechnicianSchedulePort lockTechnicianSchedulePort;
    private final Clock clock;

    public ReleaseTechnicianWorksService(
            WorkRepository workRepository, LockTechnicianSchedulePort lockTechnicianSchedulePort, Clock clock) {
        this.workRepository = workRepository;
        this.lockTechnicianSchedulePort = lockTechnicianSchedulePort;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public TechnicianWorkReleaseInfo release(ReleaseTechnicianWorksCommand command) {
        OrganizationId organizationId = new OrganizationId(command.organizationId());
        TechnicianId technicianId = new TechnicianId(command.technicianId());
        MembershipId processedBy = new MembershipId(command.processedByMembershipId());
        Cause cause = Objects.requireNonNull(command.cause(), "cause must not be null");
        requireReadCommittedAtMost();

        TechnicianScheduleLocks.lock(lockTechnicianSchedulePort, organizationId, technicianId);
        List<Work> activeWorks = workRepository.listActiveByTechnician(organizationId, technicianId).stream()
                .sorted(Comparator.comparing(ReleaseTechnicianWorksService::idOf))
                .toList();
        if (cause == Cause.ROLE_CHANGE) {
            List<Long> inProgress = activeWorks.stream()
                    .filter(work -> work.status() == WorkStatus.IN_PROGRESS)
                    .map(ReleaseTechnicianWorksService::idOf)
                    .toList();
            if (!inProgress.isEmpty()) {
                return TechnicianWorkReleaseInfo.blocked(inProgress);
            }
        }

        Instant now = clock.instant();
        List<Work> released = activeWorks.stream()
                .filter(work -> work.status() == WorkStatus.PENDING_ACCEPT || work.status() == WorkStatus.ACCEPTED)
                .toList();
        released.forEach(work -> work.unassign(now, processedBy));
        List<Work> plannedForTechnician =
                workRepository.listBacklogPlannedForTechnician(organizationId, technicianId).stream()
                        .sorted(Comparator.comparing(ReleaseTechnicianWorksService::idOf))
                        .toList();
        plannedForTechnician.forEach(Work::clearPlannedTechnician);
        released.forEach(workRepository::save);
        plannedForTechnician.forEach(workRepository::save);
        return TechnicianWorkReleaseInfo.released(
                released.stream().map(ReleaseTechnicianWorksService::idOf).toList());
    }

    private static void requireReadCommittedAtMost() {
        Integer isolationLevel = TransactionSynchronizationManager.getCurrentTransactionIsolationLevel();
        if (isolationLevel != null && isolationLevel > TransactionDefinition.ISOLATION_READ_COMMITTED) {
            throw new IllegalStateException(
                    "technician work release requires READ COMMITTED but the caller's transaction uses isolation "
                            + isolationLevel);
        }
    }

    private static Long idOf(Work work) {
        return work.id()
                .orElseThrow(() -> new IllegalStateException("stored work must have an id"))
                .value();
    }
}
