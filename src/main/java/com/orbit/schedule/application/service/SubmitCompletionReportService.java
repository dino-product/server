package com.orbit.schedule.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.schedule.application.port.in.command.SubmitCompletionReportUseCase;
import com.orbit.schedule.application.port.in.command.dto.SubmitCompletionReportCommand;
import com.orbit.schedule.application.port.out.LoadActorPort;
import com.orbit.schedule.application.port.out.WorkRepository;
import com.orbit.schedule.domain.Actor;
import com.orbit.schedule.domain.CompletionReport;
import com.orbit.schedule.domain.Money;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkStatus;

/**
 * 완료보고 제출. 담당 기사 본인이 화면에서 본 최신 배정의 작업중인 작업에 보고를 붙여 완료로 바꾸고 지금 시각을 완료 시각으로 남긴다. 보고 저장과 완료 전환은 한
 * 번의 저장으로 함께 성공·실패한다. 이미 같은 보고로 완료한 작업에 다시 보내면(중복 전송) 아무것도 바꾸지 않고 성공하고, 다른 보고면 409(SCHEDULE-002)다. 완료는
 * 기사의 활성 작업을 줄이기만 하므로 기사 잠금은 잡지 않는다. 관리자의 취소와 같은 순간에 들어온 제출은 작업 버전 검사(HM-234)로 뒤 요청을 실패시키며, 그 전까지는
 * 나중에 저장한 쪽이 이긴다. 오류 확인 순서는 schedule 지침의 기사 유즈케이스 순서를 따른다.
 */
@Service
public class SubmitCompletionReportService implements SubmitCompletionReportUseCase {

    private final LoadActorPort loadActorPort;
    private final WorkRepository workRepository;
    private final Clock clock;

    public SubmitCompletionReportService(LoadActorPort loadActorPort, WorkRepository workRepository, Clock clock) {
        this.loadActorPort = loadActorPort;
        this.workRepository = workRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void submit(SubmitCompletionReportCommand command) {
        Actor technician =
                OrganizationActors.requireTechnician(loadActorPort, command.accountId(), command.organizationId());
        Work work = OrganizationWorks.require(workRepository, technician.organizationId(), command.workId());
        AssignedTechnicians.requireEverAssigned(work, technician);
        int assignmentNumber = AssignedTechnicians.requireAssignmentNumber(command.assignmentNumber());
        CompletionReport report = DomainRuleViolations.call(() -> toReport(command));
        AssignedTechnicians.requireCurrentAssignmentOf(work, technician, assignmentNumber);
        if (work.status() == WorkStatus.COMPLETED && work.completionReport().equals(Optional.of(report))) {
            return;
        }

        Instant now = clock.instant();
        DomainRuleViolations.run(() -> work.submitCompletionReport(report, now));

        workRepository.save(work);
    }

    private static CompletionReport toReport(SubmitCompletionReportCommand command) {
        Money actualFee = command.actualFee() == null ? null : new Money(command.actualFee());
        return new CompletionReport(
                command.beforePhotos(),
                command.afterPhotos(),
                command.usedParts(),
                command.workNote(),
                actualFee,
                command.actualPaymentMethod());
    }
}
