package com.orbit.schedule.adapter.out.memory;

import java.time.Instant;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.orbit.schedule.application.port.out.WorkQueryPort;
import com.orbit.schedule.application.port.out.WorkSearchCriteria;
import com.orbit.schedule.application.port.out.WorkSearchResult;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.Work;

/**
 * 임시 WorkQueryPort 구현. 어떤 조건에도 빈 목록을 돌려준다(저장한 작업도 목록 조회에는 나오지 않는다). JPA 어댑터(HM-234)로 교체한 뒤 삭제한다.
 * {@code local}·{@code test} 프로필에서만 등록한다.
 */
@Component
@Profile({"local & !prod", "test & !prod"})
class EmptyWorkQueryAdapter implements WorkQueryPort {

    @Override
    public List<Work> listScheduledBetween(OrganizationId organizationId, Instant from, Instant to) {
        return List.of();
    }

    @Override
    public List<Work> listBacklog(OrganizationId organizationId) {
        return List.of();
    }

    @Override
    public WorkSearchResult search(OrganizationId organizationId, WorkSearchCriteria criteria) {
        return new WorkSearchResult(List.of(), 0);
    }
}
