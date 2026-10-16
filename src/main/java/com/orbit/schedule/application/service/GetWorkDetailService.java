package com.orbit.schedule.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.schedule.application.port.in.query.GetWorkDetailUseCase;
import com.orbit.schedule.application.port.in.query.dto.GetWorkDetailQuery;
import com.orbit.schedule.application.port.in.query.dto.StaffInfo;
import com.orbit.schedule.application.port.in.query.dto.WorkDetailInfo;
import com.orbit.schedule.application.port.out.LoadActorPort;
import com.orbit.schedule.application.port.out.LoadStaffProfilesPort;
import com.orbit.schedule.application.port.out.LoadTechnicianProfilesPort;
import com.orbit.schedule.application.port.out.LoadWorkTypeLabelsPort;
import com.orbit.schedule.application.port.out.PhotoUrlPort;
import com.orbit.schedule.application.port.out.WorkRepository;
import com.orbit.schedule.domain.Actor;
import com.orbit.schedule.domain.ManagerActor;
import com.orbit.schedule.domain.TechnicianActor;
import com.orbit.schedule.domain.Work;

/**
 * 작업 상세 조회. 관리자(총관리자·직원)는 조직의 모든 작업을 모든 항목과 함께 본다. 기사는 한 번이라도 배정받은 작업만 보고(아니면 다른 조직 작업처럼 404), 자기 배정 이력만 받는다. 작업유형·등록
 * 시각·지연·고객·결제·일정·진행 기록·완료보고는 지금 담당일 때만 받고, 등록자와 관리자 강제 변경 기록은 받지 않는다. 지연 여부는 지금 시각으로 계산하고, 완료보고 사진(강제 변경으로 치운 보고 포함)은
 * 내려받을 주소와 함께 담는다. 오류 확인 순서는 schedule 지침의 조회 순서를 따른다.
 */
@Service
public class GetWorkDetailService implements GetWorkDetailUseCase {

    private final LoadActorPort loadActorPort;
    private final WorkRepository workRepository;
    private final PhotoUrlPort photoUrlPort;
    private final LoadTechnicianProfilesPort loadTechnicianProfilesPort;
    private final LoadStaffProfilesPort loadStaffProfilesPort;
    private final LoadWorkTypeLabelsPort loadWorkTypeLabelsPort;
    private final Clock clock;

    public GetWorkDetailService(
            LoadActorPort loadActorPort,
            WorkRepository workRepository,
            PhotoUrlPort photoUrlPort,
            LoadTechnicianProfilesPort loadTechnicianProfilesPort,
            LoadStaffProfilesPort loadStaffProfilesPort,
            LoadWorkTypeLabelsPort loadWorkTypeLabelsPort,
            Clock clock) {
        this.loadActorPort = loadActorPort;
        this.workRepository = workRepository;
        this.photoUrlPort = photoUrlPort;
        this.loadTechnicianProfilesPort = loadTechnicianProfilesPort;
        this.loadStaffProfilesPort = loadStaffProfilesPort;
        this.loadWorkTypeLabelsPort = loadWorkTypeLabelsPort;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public WorkDetailInfo get(GetWorkDetailQuery query) {
        Actor actor = OrganizationActors.requireMember(loadActorPort, query.accountId(), query.organizationId());
        Work work = OrganizationWorks.require(workRepository, actor.organizationId(), query.workId());
        Instant now = clock.instant();
        return switch (actor) {
            case ManagerActor manager -> {
                DisplayInfos displayInfos = displayInfos(work);
                yield fullDetail(
                        work,
                        now,
                        displayInfos,
                        displayInfos.registrar(work),
                        WorkViews.assignments(work, displayInfos),
                        WorkViews.statusCorrections(work, photoUrlPort));
            }
            case TechnicianActor technician -> technicianDetail(work, now, technician);
        };
    }

    private WorkDetailInfo technicianDetail(Work work, Instant now, TechnicianActor technician) {
        AssignedTechnicians.requireEverAssigned(work, technician);
        if (WorkViews.isCurrentAssignee(work, technician)) {
            DisplayInfos displayInfos = DisplayInfos.ofCurrentTechnician(
                    loadTechnicianProfilesPort,
                    loadStaffProfilesPort,
                    loadWorkTypeLabelsPort,
                    work.organizationId(),
                    work);
            return fullDetail(
                    work,
                    now,
                    displayInfos,
                    null,
                    WorkViews.assignmentsOf(work, technician.technicianId(), displayInfos),
                    List.of());
        }
        // 지금 담당이 아니면 자기 배정만 보이므로 자기 표시 정보만 찾는다.
        DisplayInfos ownInfo =
                DisplayInfos.ofTechnician(loadTechnicianProfilesPort, work.organizationId(), technician.technicianId());
        List<WorkDetailInfo.Assignment> ownAssignments =
                WorkViews.assignmentsOf(work, technician.technicianId(), ownInfo);
        return new WorkDetailInfo(
                work.id().orElseThrow().value(),
                work.name(),
                work.status(),
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                ownAssignments,
                null,
                null,
                null,
                null,
                List.of());
    }

    private DisplayInfos displayInfos(Work work) {
        return DisplayInfos.ofDetail(
                loadTechnicianProfilesPort, loadStaffProfilesPort, loadWorkTypeLabelsPort, work.organizationId(), work);
    }

    private WorkDetailInfo fullDetail(
            Work work,
            Instant now,
            DisplayInfos displayInfos,
            StaffInfo registrar,
            List<WorkDetailInfo.Assignment> assignments,
            List<WorkDetailInfo.StatusCorrection> statusCorrections) {
        return new WorkDetailInfo(
                work.id().orElseThrow().value(),
                work.name(),
                work.status(),
                work.isDelayedAt(now),
                displayInfos.workType(work),
                registrar,
                work.registeredAt(),
                WorkViews.customer(work.customerInfo()),
                WorkViews.payment(work.paymentInfo()),
                work.schedule()
                        .map(schedule -> WorkViews.schedule(schedule, displayInfos))
                        .orElse(null),
                work.plannedSchedule()
                        .map(planned -> WorkViews.plannedSchedule(planned, displayInfos))
                        .orElse(null),
                assignments,
                work.startedAt().orElse(null),
                work.completedAt().orElse(null),
                work.cancellation().map(WorkViews::cancellation).orElse(null),
                work.completionReport()
                        .map(report -> WorkViews.completionReport(report, photoUrlPort))
                        .orElse(null),
                statusCorrections);
    }
}
