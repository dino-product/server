package com.orbit.schedule.application.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.application.port.in.command.CorrectWorkStatusUseCase;
import com.orbit.schedule.application.port.in.command.dto.CorrectWorkStatusCommand;
import com.orbit.schedule.application.port.out.LoadActorPort;
import com.orbit.schedule.application.port.out.LockTechnicianSchedulePort;
import com.orbit.schedule.application.port.out.WorkRepository;
import com.orbit.schedule.domain.ManagerActor;
import com.orbit.schedule.domain.TechnicianId;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkScheduleConflictPolicy;
import com.orbit.schedule.domain.WorkStatus;
import com.orbit.shared.error.BusinessException;

/**
 * 관리자 강제 상태 변경. 총관리자만 작업 상태를 한 단계 되돌린다(완료 → 작업중, 작업중 → 수락됨, 취소 → 대기함). 되돌리면서 치운 데이터와 전후 상태·사유·
 * 처리자·시각은 작업의 정정 기록에 남는다. 허용하지 않는 전이는 409(SCHEDULE-002)다. 작업중으로 되돌리면 그 기사의 작업중 작업이 늘어나므로, 작업 시작처럼
 * 기사를 잠근 뒤 같은 기사에게 작업중인 다른 작업이 있으면 409(SCHEDULE-011)로 거부한다. 오류 확인 순서는 schedule 지침의 공통 순서를 따른다.
 */
@Service
public class CorrectWorkStatusService implements CorrectWorkStatusUseCase {

    private final LoadActorPort loadActorPort;
    private final WorkRepository workRepository;
    private final LockTechnicianSchedulePort lockTechnicianSchedulePort;
    private final Clock clock;

    public CorrectWorkStatusService(
            LoadActorPort loadActorPort,
            WorkRepository workRepository,
            LockTechnicianSchedulePort lockTechnicianSchedulePort,
            Clock clock) {
        this.loadActorPort = loadActorPort;
        this.workRepository = workRepository;
        this.lockTechnicianSchedulePort = lockTechnicianSchedulePort;
        this.clock = clock;
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void correct(CorrectWorkStatusCommand command) {
        ManagerActor owner =
                OrganizationActors.requireOwner(loadActorPort, command.accountId(), command.organizationId());
        Work work = OrganizationWorks.require(workRepository, owner.organizationId(), command.workId());

        Instant now = clock.instant();
        DomainRuleViolations.run(
                () -> work.correctStatus(command.targetStatus(), now, owner.membershipId(), command.reason()));

        if (work.status() == WorkStatus.IN_PROGRESS) {
            TechnicianId technicianId = work.schedule()
                    .orElseThrow(() -> new IllegalStateException("IN_PROGRESS work must have a schedule"))
                    .technicianId();
            TechnicianScheduleLocks.lock(lockTechnicianSchedulePort, work.organizationId(), technicianId);
            if (WorkScheduleConflictPolicy.hasConcurrentInProgress(
                    technicianId, work, workRepository.listActiveByTechnician(work.organizationId(), technicianId))) {
                throw new BusinessException(ScheduleErrorCode.TECHNICIAN_ALREADY_WORKING);
            }
        }
        workRepository.save(work);
    }
}
