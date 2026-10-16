package com.orbit.schedule.application.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.application.port.in.query.SearchWorksUseCase;
import com.orbit.schedule.application.port.in.query.dto.SearchWorksQuery;
import com.orbit.schedule.application.port.in.query.dto.WorkSearchInfo;
import com.orbit.schedule.application.port.out.LoadActorPort;
import com.orbit.schedule.application.port.out.LoadStaffProfilesPort;
import com.orbit.schedule.application.port.out.LoadTechnicianProfilesPort;
import com.orbit.schedule.application.port.out.LoadWorkTypeLabelsPort;
import com.orbit.schedule.application.port.out.WorkQueryPort;
import com.orbit.schedule.application.port.out.WorkSearchCriteria;
import com.orbit.schedule.application.port.out.WorkSearchResult;
import com.orbit.schedule.domain.ManagerActor;
import com.orbit.shared.error.BusinessException;

/**
 * 관리자 작업 목록 검색. 검색어·상태·담당 기사·작업유형·등록자·일정 시작 구간·거절 반환·지연으로 거르고 정렬·쪽 나누기를 적용하며, 목록에 보일 담당기사·작업유형·등록자의 표시 정보를 함께 담는다. 검색
 * 조건마다 유즈케이스를 나누지 않고 이 입력으로 표현한다. 기사는 요청할 수 없다(403 SCHEDULE-005). 조건 형식이 틀리면 요청자 확인 뒤 400(SCHEDULE-003)이다. 지연은 지금 시각으로
 * 판정한다. 저장소가 목록과 전체 수를 따로 읽어도 같은 시점을 보도록 한 스냅샷(REPEATABLE READ)에서 검색한다.
 */
@Service
public class SearchWorksService implements SearchWorksUseCase {

    static final int DEFAULT_PAGE_SIZE = 20;

    private final LoadActorPort loadActorPort;
    private final WorkQueryPort workQueryPort;
    private final LoadTechnicianProfilesPort loadTechnicianProfilesPort;
    private final LoadStaffProfilesPort loadStaffProfilesPort;
    private final LoadWorkTypeLabelsPort loadWorkTypeLabelsPort;
    private final Clock clock;

    public SearchWorksService(
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
    public WorkSearchInfo search(SearchWorksQuery query) {
        ManagerActor manager =
                OrganizationActors.requireManager(loadActorPort, query.accountId(), query.organizationId());
        Instant now = clock.instant();
        WorkSearchCriteria criteria = criteria(query, now);

        WorkSearchResult result = workQueryPort.search(manager.organizationId(), criteria);
        DisplayInfos displayInfos = DisplayInfos.of(
                loadTechnicianProfilesPort,
                loadStaffProfilesPort,
                loadWorkTypeLabelsPort,
                manager.organizationId(),
                result.works().stream().map(WorkSearchResult.SearchedWork::work).toList());
        return new WorkSearchInfo(
                result.works().stream()
                        .map(searched -> WorkSearchConditions.summary(searched, now, displayInfos))
                        .toList(),
                result.totalCount(),
                criteria.page(),
                criteria.size());
    }

    private static WorkSearchCriteria criteria(SearchWorksQuery query, Instant now) {
        int page = query.page() == null ? 0 : query.page();
        int size = query.size() == null ? DEFAULT_PAGE_SIZE : query.size();
        WorkSearchCriteria criteria = WorkSearchConditions.criteria(query.filter(), now);
        if (page < 0
                || size < 1
                || size > WorkSearchCriteria.MAX_SIZE
                || (long) page * size > WorkSearchCriteria.MAX_OFFSET) {
            throw invalidInput();
        }
        return criteria.withPage(page, size);
    }

    private static BusinessException invalidInput() {
        return new BusinessException(ScheduleErrorCode.INVALID_WORK_INPUT);
    }
}
