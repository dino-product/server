package com.orbit.schedule.application.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.application.port.in.command.StartWorkUseCase;
import com.orbit.schedule.application.port.in.command.dto.StartWorkCommand;
import com.orbit.schedule.application.port.out.LoadActorPort;
import com.orbit.schedule.application.port.out.LockTechnicianSchedulePort;
import com.orbit.schedule.application.port.out.WorkRepository;
import com.orbit.schedule.domain.Actor;
import com.orbit.schedule.domain.AssignmentHistory;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkScheduleConflictPolicy;
import com.orbit.shared.error.BusinessException;

/**
 * 작업 시작. 담당 기사 본인이 화면에서 본 최신 배정의 수락된 작업을 작업중으로 바꾸고 지금 시각을 시작 시각으로 남긴다. 배정이 그대로인 채 이미 시작한 작업(완료
 * 포함)에 다시 보내면(중복 전송) 아무것도 바꾸지 않고 성공한다. 같은 기사에게 이미 작업중인 다른 작업이 있으면 409(SCHEDULE-011)다. 같은 기사의 서로 다른
 * 작업 시작이 서로를 못 보고 둘 다 작업중이 되지 않도록, 작업중 판정 전에 기사를 잠근다. 이 잠금은 같은 작업에 대한 관리자의 재배정·일정 변경·해제·취소를 막지
 * 않으며, 그 경합은 작업 버전 검사(HM-234)로 뒤 요청을 실패시키고 그 전까지는 나중에 저장한 쪽이 이긴다. 오류 확인 순서는 schedule 지침의 기사 유즈케이스
 * 순서를 따른다.
 */
@Service
public class StartWorkService implements StartWorkUseCase {

    private final LoadActorPort loadActorPort;
    private final WorkRepository workRepository;
    private final LockTechnicianSchedulePort lockTechnicianSchedulePort;
    private final Clock clock;

    public StartWorkService(
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
    @Transactional
    public void start(StartWorkCommand command) {
        Actor technician =
                OrganizationActors.requireTechnician(loadActorPort, command.accountId(), command.organizationId());
        Work work = OrganizationWorks.require(workRepository, technician.organizationId(), command.workId());
        AssignedTechnicians.requireEverAssigned(work, technician);
        int assignmentNumber = AssignedTechnicians.requireAssignmentNumber(command.assignmentNumber());
        AssignmentHistory assignment =
                AssignedTechnicians.requireCurrentAssignmentOf(work, technician, assignmentNumber);
        if (work.startedAt().isPresent() && assignment.ending().isEmpty()) {
            return;
        }

        Instant now = clock.instant();
        DomainRuleViolations.run(() -> work.start(now));

        TechnicianScheduleLocks.lock(lockTechnicianSchedulePort, work.organizationId(), technician.membershipId());
        if (WorkScheduleConflictPolicy.hasConcurrentInProgress(
                technician.membershipId(),
                work,
                workRepository.listActiveByTechnician(work.organizationId(), technician.membershipId()))) {
            throw new BusinessException(ScheduleErrorCode.TECHNICIAN_ALREADY_WORKING);
        }
        workRepository.save(work);
    }
}
