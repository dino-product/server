package com.orbit.schedule.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.schedule.application.port.in.query.GetWorkDetailUseCase;
import com.orbit.schedule.application.port.in.query.dto.CompletionReportInfo;
import com.orbit.schedule.application.port.in.query.dto.GetWorkDetailQuery;
import com.orbit.schedule.application.port.in.query.dto.WorkDetailInfo;
import com.orbit.schedule.application.port.out.LoadActorPort;
import com.orbit.schedule.application.port.out.WorkRepository;
import com.orbit.schedule.domain.Actor;
import com.orbit.schedule.domain.ManagerActor;
import com.orbit.schedule.domain.TechnicianActor;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkTypeId;

/**
 * 작업 상세 조회. 관리자(총관리자·직원)는 조직의 모든 작업을 모든 항목과 함께 본다. 기사는 한 번이라도 배정받은 작업만 보고(아니면 다른 조직 작업처럼
 * 404), 자기 배정 이력만 받는다. 고객·결제·일정·진행 기록·완료보고는 지금 담당일 때만 받고, 관리자 강제 변경 기록은 받지 않는다. 지연 여부는 지금 시각으로
 * 계산한다. 오류 확인 순서는 schedule 지침의 공통 순서(작업 조회까지)를 따른다.
 */
@Service
public class GetWorkDetailService implements GetWorkDetailUseCase {

    private final LoadActorPort loadActorPort;
    private final WorkRepository workRepository;
    private final Clock clock;

    public GetWorkDetailService(LoadActorPort loadActorPort, WorkRepository workRepository, Clock clock) {
        this.loadActorPort = loadActorPort;
        this.workRepository = workRepository;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public WorkDetailInfo get(GetWorkDetailQuery query) {
        Actor actor = OrganizationActors.requireMember(loadActorPort, query.accountId(), query.organizationId());
        Work work = OrganizationWorks.require(workRepository, actor.organizationId(), query.workId());
        Instant now = clock.instant();
        return switch (actor) {
            case ManagerActor manager -> detail(work, now, null, true);
            case TechnicianActor technician -> {
                AssignedTechnicians.requireEverAssigned(work, technician);
                yield detail(work, now, technician, WorkViews.isCurrentAssignee(work, technician));
            }
        };
    }

    /** technician이 null이면 관리자용이다. visible은 고객·일정·진행 기록을 보여 줄지다. */
    private static WorkDetailInfo detail(Work work, Instant now, TechnicianActor technician, boolean visible) {
        return new WorkDetailInfo(
                work.id().orElseThrow().value(),
                work.name(),
                work.status(),
                work.isDelayedAt(now),
                work.workType().map(WorkTypeId::value).orElse(null),
                visible ? WorkViews.customer(work.customerInfo()) : null,
                visible ? WorkViews.payment(work.paymentInfo()) : null,
                visible ? work.schedule().map(WorkViews::schedule).orElse(null) : null,
                WorkViews.assignments(work, technician),
                visible ? work.startedAt().orElse(null) : null,
                visible ? work.completedAt().orElse(null) : null,
                visible ? work.cancellation().map(WorkViews::cancellation).orElse(null) : null,
                visible
                        ? work.completionReport()
                                .map(CompletionReportInfo::from)
                                .orElse(null)
                        : null,
                technician == null ? WorkViews.statusCorrections(work) : List.of());
    }
}
