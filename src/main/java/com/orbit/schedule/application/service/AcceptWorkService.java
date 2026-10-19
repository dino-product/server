package com.orbit.schedule.application.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.schedule.application.port.in.command.AcceptWorkUseCase;
import com.orbit.schedule.application.port.in.command.dto.AcceptWorkCommand;
import com.orbit.schedule.application.port.out.LoadActorPort;
import com.orbit.schedule.application.port.out.LockTechnicianSchedulePort;
import com.orbit.schedule.application.port.out.WorkRepository;
import com.orbit.schedule.domain.AssignmentHistory;
import com.orbit.schedule.domain.AssignmentResult;
import com.orbit.schedule.domain.TechnicianActor;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkId;

/**
 * 배정 수락. 담당 기사 본인이 화면에서 본 최신 배정만 수락한다. 이미 수락해 그대로인 배정에 수락을 다시 보내면(중복 전송) 아무것도 바꾸지 않고 성공한다. 일정을
 * 바꾸지 않으므로 겹침 판정은 하지 않는다.
 *
 * <p>작업 식별자 형식을 확인한 뒤 작업을 읽기 전에 기사를 잠근다(대기 한도 초과 409 SCHEDULE-006). 같은 기사를 잠그는 대기함 반환(역할
 * 변경·비활성화·탈퇴)·같은 작업의 중복 수락과 한 줄로 서서 뒤에 온 쪽이 앞선 커밋을 보고 판정한다(반환된 작업이면 404, 이미 수락했으면 중복 전송 성공).
 * 관리자의 일정 변경도 같은 기사를 잠가 한 줄로 서므로, 수락이 늦으면 배정이 바뀌었다는 409(SCHEDULE-010), 일정 변경이 늦으면 409(SCHEDULE-016)다.
 * 재배정(새 담당기사를 잠금)·배정 해제·취소·기본정보 수정(잠그지 않음)과의 경합은 작업 버전 검사로 뒤 저장이 실패한다. 관리자 쪽 재배정·해제가
 * 늦으면 409(SCHEDULE-016)이고, 취소·기본정보 수정이 늦거나 수락이 늦으면 아직 500이다(HM-236). 오류 확인 순서는 schedule 지침의 기사 유즈케이스 순서를 따른다.
 */
@Service
public class AcceptWorkService implements AcceptWorkUseCase {

    private final LoadActorPort loadActorPort;
    private final WorkRepository workRepository;
    private final LockTechnicianSchedulePort lockTechnicianSchedulePort;
    private final Clock clock;

    public AcceptWorkService(
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
    public void accept(AcceptWorkCommand command) {
        TechnicianActor technician =
                OrganizationActors.requireTechnician(loadActorPort, command.accountId(), command.organizationId());
        WorkId workId = OrganizationWorks.requireId(command.workId());
        // 작업을 읽기 전에 잠가야 잠금을 기다리는 동안 커밋된 반환·같은 요청을 본다(먼저 읽으면 영속성 컨텍스트의 낡은 작업을 쓴다).
        TechnicianScheduleLocks.lock(
                lockTechnicianSchedulePort, technician.organizationId(), technician.technicianId());
        Work work = OrganizationWorks.require(workRepository, technician.organizationId(), workId);
        AssignedTechnicians.requireVisible(work, technician);
        int assignmentNumber = AssignedTechnicians.requireAssignmentNumber(command.assignmentNumber());
        AssignmentHistory assignment =
                AssignedTechnicians.requireCurrentAssignmentOf(work, technician, assignmentNumber);
        if (assignment.result() == AssignmentResult.ACCEPTED
                && assignment.ending().isEmpty()) {
            return;
        }

        Instant now = clock.instant();
        DomainRuleViolations.run(() -> work.accept(now));

        workRepository.save(work);
    }
}
