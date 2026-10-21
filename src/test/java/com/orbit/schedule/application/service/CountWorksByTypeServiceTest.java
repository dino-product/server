package com.orbit.schedule.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orbit.schedule.application.port.in.query.dto.CountWorksByTypeQuery;
import com.orbit.schedule.application.port.in.query.dto.WorkTypeUsageInfo;
import com.orbit.schedule.application.port.out.ClearedWorkType;
import com.orbit.schedule.application.port.out.WorkTypeUsagePort;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.WorkStatus;
import com.orbit.schedule.domain.WorkTypeId;

@DisplayName("작업 유형 삭제 영향 건수")
class CountWorksByTypeServiceTest {

    @Test
    @DisplayName("등록·배정됨·작업전은 유형 없음이 될 작업, 작업중·완료·취소는 유형을 유지할 작업으로 센다")
    void splitsCountsByStart() {
        WorkTypeUsagePort port = new WorkTypeUsagePort() {
            @Override
            public Map<WorkStatus, Long> countByStatus(OrganizationId organizationId, WorkTypeId workTypeId) {
                assertThat(organizationId).isEqualTo(new OrganizationId(100L));
                assertThat(workTypeId).isEqualTo(new WorkTypeId(5L));
                return Map.of(
                        WorkStatus.REGISTERED, 2L,
                        WorkStatus.PENDING_ACCEPT, 1L,
                        WorkStatus.ACCEPTED, 3L,
                        WorkStatus.IN_PROGRESS, 4L,
                        WorkStatus.COMPLETED, 5L,
                        WorkStatus.CANCELED, 6L);
            }

            @Override
            public List<ClearedWorkType> clearWorkType(
                    OrganizationId organizationId, WorkTypeId workTypeId, Set<WorkStatus> statuses, Instant at) {
                throw new UnsupportedOperationException();
            }
        };

        assertThat(new CountWorksByTypeService(port).count(new CountWorksByTypeQuery(100L, 5L)))
                .isEqualTo(new WorkTypeUsageInfo(6L, 15L));
    }
}
