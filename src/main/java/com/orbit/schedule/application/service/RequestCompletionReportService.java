package com.orbit.schedule.application.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.schedule.CompletionReportRequested;
import com.orbit.schedule.application.port.in.command.RequestCompletionReportUseCase;
import com.orbit.schedule.application.port.in.command.dto.CompletionReportRequestInfo;
import com.orbit.schedule.application.port.in.command.dto.RequestCompletionReportCommand;
import com.orbit.schedule.application.port.out.LoadActorPort;
import com.orbit.schedule.application.port.out.WorkRepository;
import com.orbit.schedule.domain.CompletionReportRequest;
import com.orbit.schedule.domain.ManagerActor;
import com.orbit.schedule.domain.Work;

/**
 * 완료보고 요청. 예정 종료시각이 지났는데 완료보고가 없는 작업중 작업의 담당 기사에게 사무실이 보고를 요청하고, 요청 시각·요청한 관리자를 작업에 남긴 뒤
 * {@link CompletionReportRequested}를 발행한다(알림은 구독하는 모듈이 보낸다). 그 조직의 총관리자·직원 누구나 요청하며 등록자 본인일 필요는 없다.
 * 상태·종료 시각이 맞지 않으면 409(SCHEDULE-002), 같은 작업의 마지막 요청 뒤 {@link Work#COMPLETION_REPORT_REQUEST_INTERVAL}이 지나지 않았으면
 * 429(SCHEDULE-019)이고, 거부하면 저장·발행하지 않는다. 요청 기록을 더해도 작업 버전이 오르므로(WorkMapper), 같은 작업에 동시에 들어온 요청이나 기사의
 * 완료보고와 겹친 요청은 저장 자리에서 409(SCHEDULE-016)로 실패해 간격 규칙과 "보고 없음" 조건이 지켜진다. 오류 확인 순서는 schedule 지침의 공통
 * 순서를 따르고, 요청 간격은 상태·시각 순서 확인 뒤에 본다.
 */
@Service
public class RequestCompletionReportService implements RequestCompletionReportUseCase {

    private final LoadActorPort loadActorPort;
    private final WorkRepository workRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public RequestCompletionReportService(
            LoadActorPort loadActorPort,
            WorkRepository workRepository,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.loadActorPort = loadActorPort;
        this.workRepository = workRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public CompletionReportRequestInfo request(RequestCompletionReportCommand command) {
        ManagerActor actor =
                OrganizationActors.requireManager(loadActorPort, command.accountId(), command.organizationId());
        Work work = OrganizationWorks.require(workRepository, actor.organizationId(), command.workId());

        Instant now = clock.instant();
        CompletionReportRequest request =
                DomainRuleViolations.call(() -> work.requestCompletionReport(now, actor.membershipId()));

        WorkVersions.save(workRepository, work);
        eventPublisher.publishEvent(WorkEvents.completionReportRequested(work, request));
        eventPublisher.publishEvent(WorkEvents.changed(work, now));
        return new CompletionReportRequestInfo(request.requestedAt());
    }
}
