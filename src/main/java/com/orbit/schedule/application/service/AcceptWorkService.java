package com.orbit.schedule.application.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.schedule.application.port.in.command.AcceptWorkUseCase;
import com.orbit.schedule.application.port.in.command.dto.AcceptWorkCommand;
import com.orbit.schedule.application.port.out.LoadActorPort;
import com.orbit.schedule.application.port.out.WorkRepository;
import com.orbit.schedule.domain.Actor;
import com.orbit.schedule.domain.AssignmentHistory;
import com.orbit.schedule.domain.AssignmentResult;
import com.orbit.schedule.domain.Work;

/**
 * 배정 수락. 담당 기사 본인이 화면에서 본 최신 배정만 수락한다. 이미 수락해 그대로인 배정에 수락을 다시 보내면(중복 전송) 아무것도 바꾸지 않고 성공한다. 일정을
 * 바꾸지 않으므로 기사 일정 잠금·겹침 판정은 하지 않는다. 관리자의 재배정·일정 변경·해제와 같은 순간에 들어온 수락은 작업 버전 검사(HM-234)로 뒤 요청을
 * 실패시키며, 그 전까지는 나중에 저장한 쪽이 이긴다. 오류 확인 순서는 schedule 지침의 기사 유즈케이스 순서를 따른다.
 */
@Service
public class AcceptWorkService implements AcceptWorkUseCase {

    private final LoadActorPort loadActorPort;
    private final WorkRepository workRepository;
    private final Clock clock;

    public AcceptWorkService(LoadActorPort loadActorPort, WorkRepository workRepository, Clock clock) {
        this.loadActorPort = loadActorPort;
        this.workRepository = workRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void accept(AcceptWorkCommand command) {
        Actor technician =
                OrganizationActors.requireTechnician(loadActorPort, command.accountId(), command.organizationId());
        Work work = OrganizationWorks.require(workRepository, technician.organizationId(), command.workId());
        AssignedTechnicians.requireEverAssigned(work, technician);
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
