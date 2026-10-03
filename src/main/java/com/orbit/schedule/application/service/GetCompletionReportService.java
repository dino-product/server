package com.orbit.schedule.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.application.port.in.query.GetCompletionReportUseCase;
import com.orbit.schedule.application.port.in.query.dto.GetCompletionReportQuery;
import com.orbit.schedule.application.port.in.query.dto.WorkCompletionReportInfo;
import com.orbit.schedule.application.port.out.LoadActorPort;
import com.orbit.schedule.application.port.out.PhotoUrlPort;
import com.orbit.schedule.application.port.out.WorkRepository;
import com.orbit.schedule.domain.Actor;
import com.orbit.schedule.domain.CompletionReport;
import com.orbit.schedule.domain.TechnicianActor;
import com.orbit.schedule.domain.Work;
import com.orbit.shared.error.BusinessException;

/**
 * 완료보고 조회. 관리자는 조직의 모든 작업의 보고를 본다. 기사는 배정받은 적 없는 작업이면 404(SCHEDULE-001), 지금 담당이 아니면 403(SCHEDULE-009)이다.
 * 보고가 없으면(완료 전이거나 강제 변경으로 되돌림) 404(SCHEDULE-012)다. 오류 확인 순서는 schedule 지침의 조회 순서를 따른다.
 */
@Service
public class GetCompletionReportService implements GetCompletionReportUseCase {

    private final LoadActorPort loadActorPort;
    private final WorkRepository workRepository;
    private final PhotoUrlPort photoUrlPort;

    public GetCompletionReportService(
            LoadActorPort loadActorPort, WorkRepository workRepository, PhotoUrlPort photoUrlPort) {
        this.loadActorPort = loadActorPort;
        this.workRepository = workRepository;
        this.photoUrlPort = photoUrlPort;
    }

    @Override
    @Transactional(readOnly = true)
    public WorkCompletionReportInfo get(GetCompletionReportQuery query) {
        Actor actor = OrganizationActors.requireMember(loadActorPort, query.accountId(), query.organizationId());
        Work work = OrganizationWorks.require(workRepository, actor.organizationId(), query.workId());
        if (actor instanceof TechnicianActor technician) {
            AssignedTechnicians.requireEverAssigned(work, technician);
            if (!WorkViews.isCurrentAssignee(work, technician)) {
                throw new BusinessException(ScheduleErrorCode.NOT_ASSIGNED_TECHNICIAN);
            }
        }
        CompletionReport report = work.completionReport()
                .orElseThrow(() -> new BusinessException(ScheduleErrorCode.COMPLETION_REPORT_NOT_FOUND));
        // 완료된 작업은 재배정·해제할 수 없으므로 현재 일정의 기사가 보고를 제출한 기사다.
        return new WorkCompletionReportInfo(
                work.id().orElseThrow().value(),
                work.schedule().orElseThrow().technicianId().value(),
                work.completedAt().orElseThrow(),
                WorkViews.completionReport(report, photoUrlPort));
    }
}
