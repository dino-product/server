package com.orbit.schedule.application.service;

import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.schedule.application.port.in.query.CountWorksByTypeUseCase;
import com.orbit.schedule.application.port.in.query.dto.CountWorksByTypeQuery;
import com.orbit.schedule.application.port.in.query.dto.WorkTypeUsageInfo;
import com.orbit.schedule.application.port.out.WorkTypeUsagePort;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.WorkStatus;
import com.orbit.schedule.domain.WorkTypeId;

/** 작업 유형 삭제 확인 창의 영향 건수. 시작 전·시작한 작업으로 나누는 기준은 {@link WorkStatus#isBeforeStart}다. */
@Service
public class CountWorksByTypeService implements CountWorksByTypeUseCase {

    private final WorkTypeUsagePort workTypeUsagePort;

    public CountWorksByTypeService(WorkTypeUsagePort workTypeUsagePort) {
        this.workTypeUsagePort = workTypeUsagePort;
    }

    @Override
    @Transactional(readOnly = true)
    public WorkTypeUsageInfo count(CountWorksByTypeQuery query) {
        Map<WorkStatus, Long> counts = workTypeUsagePort.countByStatus(
                new OrganizationId(query.organizationId()), new WorkTypeId(query.workTypeId()));
        long beforeStart = 0;
        long started = 0;
        for (Map.Entry<WorkStatus, Long> count : counts.entrySet()) {
            if (count.getKey().isBeforeStart()) {
                beforeStart += count.getValue();
            } else {
                started += count.getValue();
            }
        }
        return new WorkTypeUsageInfo(beforeStart, started);
    }
}
