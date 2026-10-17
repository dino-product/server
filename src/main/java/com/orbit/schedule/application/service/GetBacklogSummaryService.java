package com.orbit.schedule.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.application.port.in.query.GetBacklogSummaryUseCase;
import com.orbit.schedule.application.port.in.query.dto.BacklogSummaryInfo;
import com.orbit.schedule.application.port.in.query.dto.GetBacklogSummaryQuery;
import com.orbit.schedule.application.port.out.LoadActorPort;
import com.orbit.schedule.application.port.out.LoadStaffProfilesPort;
import com.orbit.schedule.application.port.out.LoadTechnicianProfilesPort;
import com.orbit.schedule.application.port.out.LoadWorkTypeLabelsPort;
import com.orbit.schedule.application.port.out.WorkQueryPort;
import com.orbit.schedule.application.port.out.WorkSearchCriteria;
import com.orbit.schedule.application.port.out.WorkSearchResult;
import com.orbit.schedule.domain.ManagerActor;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkStatus;
import com.orbit.shared.error.BusinessException;

/**
 * 작업 목록 위에 고정하는 대기함 요약 조회. 대기함 작업 수(거절로 돌아온 작업 포함)와 그 가운데 거절로 돌아온 작업 수·나머지 수, 오래된 순으로 쪽을
 * 나눈 대기함 카드를 돌려준다. 건수와 카드가 같은 시점을 보도록 한 스냅샷(REPEATABLE READ)에서 읽는다. 기사는 요청할 수 없다(403
 * SCHEDULE-005). 쪽 입력이 틀리면 요청자 확인 뒤 400(SCHEDULE-003)이다.
 */
@Service
public class GetBacklogSummaryService implements GetBacklogSummaryUseCase {

    static final int DEFAULT_PAGE_SIZE = 5;

    private final LoadActorPort loadActorPort;
    private final WorkQueryPort workQueryPort;
    private final LoadTechnicianProfilesPort loadTechnicianProfilesPort;
    private final LoadStaffProfilesPort loadStaffProfilesPort;
    private final LoadWorkTypeLabelsPort loadWorkTypeLabelsPort;
    private final Clock clock;

    public GetBacklogSummaryService(
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
    public BacklogSummaryInfo get(GetBacklogSummaryQuery query) {
        ManagerActor manager =
                OrganizationActors.requireManager(loadActorPort, query.accountId(), query.organizationId());
        int page = query.page() == null ? 0 : query.page();
        int size = query.size() == null ? DEFAULT_PAGE_SIZE : query.size();
        if (page < 0
                || size < 1
                || size > WorkSearchCriteria.MAX_SIZE
                || (long) page * size > WorkSearchCriteria.MAX_OFFSET) {
            throw new BusinessException(ScheduleErrorCode.INVALID_WORK_INPUT);
        }
        Instant now = clock.instant();

        WorkSearchResult backlog = workQueryPort.search(manager.organizationId(), backlogCriteria(false, page, size));
        long rejectedCount = workQueryPort.count(manager.organizationId(), backlogCriteria(true, 0, 1));
        List<Work> works = backlog.works().stream()
                .map(WorkSearchResult.SearchedWork::work)
                .toList();
        DisplayInfos displayInfos = DisplayInfos.of(
                loadTechnicianProfilesPort,
                loadStaffProfilesPort,
                loadWorkTypeLabelsPort,
                manager.organizationId(),
                works);
        return new BacklogSummaryInfo(
                backlog.totalCount(),
                backlog.totalCount() - rejectedCount,
                rejectedCount,
                works.stream()
                        .map(work -> BacklogWorks.card(work, now, displayInfos))
                        .toList(),
                page,
                size);
    }

    private static WorkSearchCriteria backlogCriteria(boolean rejectedOnly, int page, int size) {
        return new WorkSearchCriteria(
                null,
                Set.of(WorkStatus.REGISTERED),
                null,
                null,
                null,
                null,
                null,
                rejectedOnly,
                null,
                WorkSearchCriteria.Sort.REGISTERED_ASC,
                page,
                size);
    }
}
