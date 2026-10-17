package com.orbit.schedule.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.application.port.in.query.ExportWorksUseCase;
import com.orbit.schedule.application.port.in.query.dto.ExportWorksQuery;
import com.orbit.schedule.application.port.in.query.dto.WorkExportInfo;
import com.orbit.schedule.application.port.out.LoadActorPort;
import com.orbit.schedule.application.port.out.LoadStaffProfilesPort;
import com.orbit.schedule.application.port.out.LoadTechnicianProfilesPort;
import com.orbit.schedule.application.port.out.LoadWorkTypeLabelsPort;
import com.orbit.schedule.application.port.out.WorkQueryPort;
import com.orbit.schedule.application.port.out.WorkSearchCriteria;
import com.orbit.schedule.application.port.out.WorkSearchResult;
import com.orbit.schedule.domain.ManagerActor;
import com.orbit.schedule.domain.Money;
import com.orbit.schedule.domain.Work;
import com.orbit.shared.error.BusinessException;

/**
 * 작업내역 출력([작업] §6, JOB-008). 작업 목록 검색과 같은 조건·정렬로 거른 결과 전체를 출력할 줄로 담는다. 금액·결제 방식은 등록 때 정한 계획값이다. 한 번에
 * {@link #MAX_ROWS}건까지만 출력하고 넘으면 400(SCHEDULE-015)이다. 목록과 표시 정보가 같은 시점을 보도록 한 스냅샷(REPEATABLE READ)에서 읽는다. 기사는
 * 요청할 수 없다(403 SCHEDULE-005). 조건 형식이 틀리면 요청자 확인 뒤 400(SCHEDULE-003)이다.
 */
@Service
public class ExportWorksService implements ExportWorksUseCase {

    /** 한 번에 출력할 수 있는 가장 많은 작업 수. */
    static final int MAX_ROWS = 10_000;

    private final LoadActorPort loadActorPort;
    private final WorkQueryPort workQueryPort;
    private final LoadTechnicianProfilesPort loadTechnicianProfilesPort;
    private final LoadStaffProfilesPort loadStaffProfilesPort;
    private final LoadWorkTypeLabelsPort loadWorkTypeLabelsPort;
    private final Clock clock;

    public ExportWorksService(
            LoadActorPort loadActorPort,
            WorkQueryPort workQueryPort,
            LoadTechnicianProfilesPort loadTechnicianProfilesPort,
            LoadStaffProfilesPort loadStaffProfilesPort,
            LoadWorkTypeLabelsPort loadWorkTypeLabelsPort,
            Clock clock) {
        this.loadActorPort = loadActorPort;
        this.workQueryPort = workQueryPort;
        this.loadTechnicianProfilesPort = loadTechnicianProfilesPort;
        this.loadStaffProfilesPort = loadStaffProfilesPort;
        this.loadWorkTypeLabelsPort = loadWorkTypeLabelsPort;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public WorkExportInfo export(ExportWorksQuery query) {
        ManagerActor manager =
                OrganizationActors.requireManager(loadActorPort, query.accountId(), query.organizationId());
        Instant now = clock.instant();
        WorkSearchCriteria criteria = WorkSearchConditions.criteria(query.filter(), now);

        // 상한보다 한 건 더 읽어 넘는지만 본다. 따로 세지 않는다.
        List<WorkSearchResult.SearchedWork> works =
                workQueryPort.listMatching(manager.organizationId(), criteria, MAX_ROWS + 1);
        if (works.size() > MAX_ROWS) {
            throw new BusinessException(ScheduleErrorCode.EXPORT_TOO_LARGE);
        }
        DisplayInfos displayInfos = DisplayInfos.of(
                loadTechnicianProfilesPort,
                loadStaffProfilesPort,
                loadWorkTypeLabelsPort,
                manager.organizationId(),
                works.stream().map(WorkSearchResult.SearchedWork::work).toList());
        return new WorkExportInfo(
                works.stream().map(searched -> row(searched, now, displayInfos)).toList(), now);
    }

    private static WorkExportInfo.Row row(
            WorkSearchResult.SearchedWork searched, Instant now, DisplayInfos displayInfos) {
        Work work = searched.work();
        return new WorkExportInfo.Row(
                WorkSearchConditions.summary(searched, now, displayInfos),
                work.paymentInfo().fee().map(Money::won).orElse(null),
                work.paymentInfo().method().orElse(null));
    }
}
