package com.orbit.schedule.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.orbit.schedule.application.port.in.command.ReleaseDeletedWorkTypeUseCase;
import com.orbit.schedule.application.port.in.command.dto.ReleaseDeletedWorkTypeCommand;
import com.orbit.schedule.application.port.in.command.dto.ReleasedWorkTypeInfo;
import com.orbit.schedule.application.port.out.ClearedWorkType;
import com.orbit.schedule.application.port.out.WorkTypeUsagePort;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.WorkStatus;
import com.orbit.schedule.domain.WorkTypeId;

/**
 * 작업 유형이 삭제되면 그 유형을 쓰는 시작 전 작업(등록·수락대기·수락됨)을 유형 없음으로 바꾼다. 작업중·완료·취소 작업은 삭제된 유형을 그대로 둔다([조직·계정] §7).
 * 작업을 하나씩 읽어 저장하지 않고 한 문장으로 바꿔, 동시에 시작·배정된 작업도 그 커밋을 기다린 뒤의 상태로 판단하고 버전 충돌로 삭제를 실패시키지 않는다. 버전을
 * 올리므로 해제 전에 작업을 읽은 다른 변경(기본정보 수정 등)은 저장 자리에서 충돌로 드러나 삭제된 유형을 되살리지 않는다.
 *
 * <p>organization의 트랜잭션 안에서만 실행해 유형 삭제와 함께 커밋·롤백된다(바깥 트랜잭션이 없으면 Spring이 거부한다). 기다린 뒤 커밋된 작업을 보려면 READ
 * COMMITTED여야 하므로 바깥 트랜잭션이 그보다 높은 격리 수준이면 거부한다.
 */
@Service
public class ReleaseDeletedWorkTypeService implements ReleaseDeletedWorkTypeUseCase {

    private static final Set<WorkStatus> BEFORE_START = Arrays.stream(WorkStatus.values())
            .filter(WorkStatus::isBeforeStart)
            .collect(Collectors.toUnmodifiableSet());

    private final WorkTypeUsagePort workTypeUsagePort;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public ReleaseDeletedWorkTypeService(
            WorkTypeUsagePort workTypeUsagePort, ApplicationEventPublisher eventPublisher, Clock clock) {
        this.workTypeUsagePort = workTypeUsagePort;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<ReleasedWorkTypeInfo> release(ReleaseDeletedWorkTypeCommand command) {
        OrganizationId organizationId = new OrganizationId(command.organizationId());
        WorkTypeId workTypeId = new WorkTypeId(command.workTypeId());
        Integer isolationLevel = TransactionSynchronizationManager.getCurrentTransactionIsolationLevel();
        if (isolationLevel != null && isolationLevel > TransactionDefinition.ISOLATION_READ_COMMITTED) {
            throw new IllegalStateException(
                    "work type release requires READ COMMITTED but the caller's transaction uses isolation "
                            + isolationLevel);
        }

        Instant now = clock.instant();
        List<ClearedWorkType> cleared =
                workTypeUsagePort.clearWorkType(organizationId, workTypeId, BEFORE_START, now).stream()
                        .sorted(Comparator.comparing(work -> work.workId().value()))
                        .toList();
        WorkEvents.publishChanged(
                eventPublisher,
                organizationId,
                cleared.stream().map(ClearedWorkType::workId).toList(),
                now);
        return cleared.stream().map(ReleaseDeletedWorkTypeService::toInfo).toList();
    }

    /** 대기함 작업의 기사 칸은 담당이 아니라 미리 입력한 값이나 마지막 배정이므로 담당 기사로 돌려주지 않는다. */
    private static ReleasedWorkTypeInfo toInfo(ClearedWorkType cleared) {
        Long technicianId = cleared.status().isActive() && cleared.technicianId() != null
                ? cleared.technicianId().value()
                : null;
        return new ReleasedWorkTypeInfo(cleared.workId().value(), technicianId);
    }
}
