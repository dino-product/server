package com.orbit.schedule.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.media.MediaFiles;
import com.orbit.media.StagedFiles;
import com.orbit.schedule.application.port.in.command.dto.SubmitCompletionReportCommand;
import com.orbit.schedule.application.port.out.LoadActorPort;
import com.orbit.schedule.application.port.out.WorkRepository;
import com.orbit.schedule.domain.CompletionReport;
import com.orbit.schedule.domain.Money;
import com.orbit.schedule.domain.TechnicianActor;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkStatus;

/**
 * 완료보고 제출의 트랜잭션 부분({@link SubmitCompletionReportService}가 사진 확인을 마친 뒤 부른다). 규칙은
 * {@link SubmitCompletionReportService}가 설명한다. 사진의 확인 결과는 다른 보고 항목과 함께 검증하고, 작업을 저장한 같은 트랜잭션에서 사진을 작업에 연결한 뒤
 * 이벤트를 발행한다. 같은 보고의 중복 전송은 저장하지 않으므로 발행하지 않는다.
 */
@Component
public class SubmitCompletionReportTransaction {

    private final LoadActorPort loadActorPort;
    private final WorkRepository workRepository;
    private final MediaFiles mediaFiles;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public SubmitCompletionReportTransaction(
            LoadActorPort loadActorPort,
            WorkRepository workRepository,
            MediaFiles mediaFiles,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.loadActorPort = loadActorPort;
        this.workRepository = workRepository;
        this.mediaFiles = mediaFiles;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /** @param photos 트랜잭션 밖에서 확인한 작업 전·후 사진 */
    @Transactional
    public void submit(SubmitCompletionReportCommand command, StagedFiles photos) {
        TechnicianActor technician =
                OrganizationActors.requireTechnician(loadActorPort, command.accountId(), command.organizationId());
        Work work = OrganizationWorks.require(workRepository, technician.organizationId(), command.workId());
        AssignedTechnicians.requireVisible(work, technician);
        int assignmentNumber = AssignedTechnicians.requireAssignmentNumber(command.assignmentNumber());
        CompletionReport report = DomainRuleViolations.call(() -> toReport(command));
        WorkPhotos.requireAccepted(photos);
        AssignedTechnicians.requireCurrentAssignmentOf(work, technician, assignmentNumber);
        if (work.status() == WorkStatus.COMPLETED && work.completionReport().equals(Optional.of(report))) {
            return;
        }

        Instant now = clock.instant();
        DomainRuleViolations.run(() -> work.submitCompletionReport(report, now));

        workRepository.save(work);
        WorkPhotos.attach(mediaFiles, photos, work.id().orElseThrow());
        eventPublisher.publishEvent(WorkEvents.completionReportSubmitted(work, technician.technicianId(), now));
        eventPublisher.publishEvent(WorkEvents.changed(work, now));
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
