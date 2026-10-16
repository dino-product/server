package com.orbit.schedule.application.service;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.application.port.in.query.dto.WorkSearchFilter;
import com.orbit.schedule.application.port.in.query.dto.WorkSearchInfo;
import com.orbit.schedule.application.port.out.WorkSearchCriteria;
import com.orbit.schedule.application.port.out.WorkSearchResult;
import com.orbit.schedule.domain.MembershipId;
import com.orbit.schedule.domain.TechnicianId;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkSchedule;
import com.orbit.schedule.domain.WorkTypeId;
import com.orbit.shared.error.BusinessException;

/** 작업 목록 조건을 검증·정규화해 저장소 조건으로 바꾸고, 검색된 작업을 목록 줄로 옮긴다. 작업 목록 검색과 작업내역 출력이 함께 쓴다. */
final class WorkSearchConditions {

    static final int MAX_KEYWORD_LENGTH = 100;

    private WorkSearchConditions() {}

    /**
     * 조건이 틀리면 400(SCHEDULE-003). 지연만 보기는 now로 판정한다. 쪽은 첫 쪽 한 건으로 채우며, 쪽을 나누는 호출자가 범위를 확인하고
     * {@link WorkSearchCriteria#withPage}로 정한다.
     */
    static WorkSearchCriteria criteria(WorkSearchFilter filter, Instant now) {
        WorkSearchFilter conditions = filter == null ? WorkSearchFilter.none() : filter;
        String keyword =
                conditions.keyword() == null ? null : conditions.keyword().strip();
        if (keyword != null && keyword.length() > MAX_KEYWORD_LENGTH) {
            throw invalidInput();
        }
        TechnicianId technicianId = conditions.technicianId() == null
                ? null
                : DomainRuleViolations.call(() -> new TechnicianId(conditions.technicianId()));
        WorkTypeId workTypeId = conditions.workTypeId() == null
                ? null
                : DomainRuleViolations.call(() -> new WorkTypeId(conditions.workTypeId()));
        MembershipId registrarId = conditions.registrarId() == null
                ? null
                : DomainRuleViolations.call(() -> new MembershipId(conditions.registrarId()));
        QueryPeriod startPeriod = conditions.startFrom() == null && conditions.startTo() == null
                ? null
                : QueryPeriod.ofAnyLength(conditions.startFrom(), conditions.startTo());
        if (conditions.statuses() != null && conditions.statuses().stream().anyMatch(Objects::isNull)) {
            throw invalidInput();
        }
        return new WorkSearchCriteria(
                keyword == null || keyword.isEmpty() ? null : keyword,
                conditions.statuses() == null ? Set.of() : conditions.statuses(),
                technicianId,
                workTypeId,
                registrarId,
                startPeriod == null ? null : startPeriod.from(),
                startPeriod == null ? null : startPeriod.to(),
                conditions.returnedByRejectionOnly(),
                conditions.delayedOnly() ? now : null,
                sort(conditions.sort()),
                0,
                1);
    }

    static WorkSearchInfo.WorkSummary summary(
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

    private static WorkSearchCriteria.Sort sort(WorkSearchFilter.Sort sort) {
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

    private static BusinessException invalidInput() {
        return new BusinessException(ScheduleErrorCode.INVALID_WORK_INPUT);
    }
}
