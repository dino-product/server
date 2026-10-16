package com.orbit.schedule.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

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
import com.orbit.schedule.domain.MembershipId;
import com.orbit.schedule.domain.TechnicianId;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkSchedule;
import com.orbit.schedule.domain.WorkTypeId;
import com.orbit.shared.error.BusinessException;

/**
 * 관리자 작업 목록 검색. 검색어·상태·담당 기사·작업유형·등록자·일정 시작 구간·거절 반환·지연으로 거르고 정렬·쪽 나누기를 적용하며, 목록에 보일 담당기사·작업유형·등록자의 표시 정보를 함께 담는다. 검색
 * 조건마다 유즈케이스를 나누지 않고 이 입력으로 표현한다. 기사는 요청할 수 없다(403 SCHEDULE-005). 조건 형식이 틀리면 요청자 확인 뒤 400(SCHEDULE-003)이다. 지연은 지금 시각으로
 * 판정한다. 저장소가 목록과 전체 수를 따로 읽어도 같은 시점을 보도록 한 스냅샷(REPEATABLE READ)에서 검색한다.
 */
@Service
public class SearchWorksService implements SearchWorksUseCase {

    static final int MAX_KEYWORD_LENGTH = 100;
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
                        .map(searched -> summary(searched, now, displayInfos))
                        .toList(),
                result.totalCount(),
                criteria.page(),
                criteria.size());
    }

    private static WorkSearchCriteria criteria(SearchWorksQuery query, Instant now) {
        String keyword = query.keyword() == null ? null : query.keyword().strip();
        if (keyword != null && keyword.length() > MAX_KEYWORD_LENGTH) {
            throw invalidInput();
        }
        TechnicianId technicianId = query.technicianId() == null
                ? null
                : DomainRuleViolations.call(() -> new TechnicianId(query.technicianId()));
        WorkTypeId workTypeId =
                query.workTypeId() == null ? null : DomainRuleViolations.call(() -> new WorkTypeId(query.workTypeId()));
        MembershipId registrarId = query.registrarId() == null
                ? null
                : DomainRuleViolations.call(() -> new MembershipId(query.registrarId()));
        QueryPeriod startPeriod = query.startFrom() == null && query.startTo() == null
                ? null
                : QueryPeriod.ofAnyLength(query.startFrom(), query.startTo());
        if (query.statuses() != null && query.statuses().stream().anyMatch(Objects::isNull)) {
            throw invalidInput();
        }
        int page = query.page() == null ? 0 : query.page();
        int size = query.size() == null ? DEFAULT_PAGE_SIZE : query.size();
        if (page < 0
                || size < 1
                || size > WorkSearchCriteria.MAX_SIZE
                || (long) page * size > WorkSearchCriteria.MAX_OFFSET) {
            throw invalidInput();
        }
        return new WorkSearchCriteria(
                keyword == null || keyword.isEmpty() ? null : keyword,
                query.statuses() == null ? Set.of() : query.statuses(),
                technicianId,
                workTypeId,
                registrarId,
                startPeriod == null ? null : startPeriod.from(),
                startPeriod == null ? null : startPeriod.to(),
                query.returnedByRejectionOnly(),
                query.delayedOnly() ? now : null,
                sort(query.sort()),
                page,
                size);
    }

    private static WorkSearchCriteria.Sort sort(SearchWorksQuery.Sort sort) {
        if (sort == null) {
            return WorkSearchCriteria.Sort.REGISTERED_DESC;
        }
        return switch (sort) {
            case REGISTERED_DESC -> WorkSearchCriteria.Sort.REGISTERED_DESC;
            case REGISTERED_ASC -> WorkSearchCriteria.Sort.REGISTERED_ASC;
            case START_TIME_ASC -> WorkSearchCriteria.Sort.START_TIME_ASC;
            case LAST_MODIFIED_DESC -> WorkSearchCriteria.Sort.LAST_MODIFIED_DESC;
        };
    }

    private static WorkSearchInfo.WorkSummary summary(
            WorkSearchResult.SearchedWork searched, Instant now, DisplayInfos displayInfos) {
        Work work = searched.work();
        WorkSchedule schedule = work.schedule().orElse(null);
        return new WorkSearchInfo.WorkSummary(
                work.id().orElseThrow().value(),
                work.name(),
                work.status(),
                schedule == null ? null : displayInfos.assignee(schedule),
                schedule == null ? null : schedule.startTime(),
                schedule == null ? null : schedule.endTime(),
                work.customerInfo().name().orElse(null),
                displayInfos.workType(work),
                displayInfos.registrar(work),
                work.registeredAt(),
                searched.lastModifiedAt(),
                work.isDelayedAt(now),
                work.isReturnedByRejection());
    }

    private static BusinessException invalidInput() {
        return new BusinessException(ScheduleErrorCode.INVALID_WORK_INPUT);
    }
}
