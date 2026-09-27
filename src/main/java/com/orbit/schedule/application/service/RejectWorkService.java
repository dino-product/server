package com.orbit.schedule.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.schedule.application.port.in.command.RejectWorkUseCase;
import com.orbit.schedule.application.port.in.command.dto.RejectWorkCommand;
import com.orbit.schedule.application.port.out.LoadActorPort;
import com.orbit.schedule.application.port.out.WorkRepository;
import com.orbit.schedule.domain.Actor;
import com.orbit.schedule.domain.AssignmentHistory;
import com.orbit.schedule.domain.AssignmentResult;
import com.orbit.schedule.domain.Rejection;
import com.orbit.schedule.domain.Work;

/**
 * 배정 거절. 담당 기사 본인이 화면에서 본 최신 배정만 사유와 함께 거절하고, 작업은 대기함으로 돌아간다. 사유는 필수이고 기타면 메모도 필수다. 같은 사유·메모로 이미
 * 거절한 배정에 다시 보내면(중복 전송) 아무것도 바꾸지 않고 성공한다. 관리자 조치와 같은 순간에 들어온 거절은 작업 버전 검사(HM-234)로 뒤 요청을 실패시키며,
 * 그 전까지는 나중에 저장한 쪽이 이긴다. 오류 확인 순서는 schedule 지침의 기사 유즈케이스 순서를 따른다.
 */
@Service
public class RejectWorkService implements RejectWorkUseCase {

    private final LoadActorPort loadActorPort;
    private final WorkRepository workRepository;
    private final Clock clock;

    public RejectWorkService(LoadActorPort loadActorPort, WorkRepository workRepository, Clock clock) {
        this.loadActorPort = loadActorPort;
        this.workRepository = workRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void reject(RejectWorkCommand command) {
        Actor technician =
                OrganizationActors.requireTechnician(loadActorPort, command.accountId(), command.organizationId());
        Work work = OrganizationWorks.require(workRepository, technician.organizationId(), command.workId());
        AssignedTechnicians.requireEverAssigned(work, technician);
        int assignmentNumber = AssignedTechnicians.requireAssignmentNumber(command.assignmentNumber());
        Rejection rejection = DomainRuleViolations.call(() -> new Rejection(command.reason(), command.note()));
        AssignmentHistory assignment =
                AssignedTechnicians.requireCurrentAssignmentOf(work, technician, assignmentNumber);
        if (assignment.result() == AssignmentResult.REJECTED
                && assignment.rejection().equals(Optional.of(rejection))) {
            return;
        }

        Instant now = clock.instant();
        DomainRuleViolations.run(() -> work.reject(rejection, now));

        workRepository.save(work);
    }
}
