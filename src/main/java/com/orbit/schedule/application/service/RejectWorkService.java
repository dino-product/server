package com.orbit.schedule.application.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.schedule.application.port.in.command.RejectWorkUseCase;
import com.orbit.schedule.application.port.in.command.dto.RejectWorkCommand;
import com.orbit.schedule.application.port.out.LoadActorPort;
import com.orbit.schedule.application.port.out.WorkRepository;
import com.orbit.schedule.domain.Rejection;
import com.orbit.schedule.domain.TechnicianActor;
import com.orbit.schedule.domain.Work;

/**
 * 배정 거절. 담당 기사 본인이 화면에서 본 최신 배정만 사유와 함께 거절하고, 작업은 대기함으로 돌아간다. 사유는 필수이고 기타면 메모도 필수다. 거절한 작업은 그
 * 기사가 볼 수 없는 작업이 되므로, 거절을 다시 보내면(중복 전송 포함) 다른 요청처럼 404(SCHEDULE-001)다. 관리자 조치와 같은 순간에 들어온 거절은 작업 버전
 * 검사(HM-236)로 뒤 요청을 실패시키며, 겹친 뒤 요청은 아직 500으로 실패한다. 오류 확인 순서는 schedule 지침의 기사 유즈케이스 순서를 따른다.
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
        TechnicianActor technician =
                OrganizationActors.requireTechnician(loadActorPort, command.accountId(), command.organizationId());
        Work work = OrganizationWorks.require(workRepository, technician.organizationId(), command.workId());
        AssignedTechnicians.requireVisible(work, technician);
        int assignmentNumber = AssignedTechnicians.requireAssignmentNumber(command.assignmentNumber());
        Rejection rejection = DomainRuleViolations.call(() -> new Rejection(command.reason(), command.note()));
        AssignedTechnicians.requireCurrentAssignmentOf(work, technician, assignmentNumber);

        Instant now = clock.instant();
        DomainRuleViolations.run(() -> work.reject(rejection, now));

        workRepository.save(work);
    }
}
