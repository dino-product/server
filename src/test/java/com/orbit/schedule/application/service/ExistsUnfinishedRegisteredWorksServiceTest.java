package com.orbit.schedule.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orbit.schedule.application.port.in.query.dto.UnfinishedRegisteredWorksQuery;
import com.orbit.schedule.application.port.out.RegisteredWorksPort;
import com.orbit.schedule.domain.MembershipId;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.WorkStatus;

@DisplayName("직원 등록 작업 중 끝나지 않은 작업 확인")
class ExistsUnfinishedRegisteredWorksServiceTest {

    @Test
    @DisplayName("대기함·배정됨·작업전·작업중인 그 직원의 등록 작업을 찾고 완료·취소 작업은 보지 않는다")
    void looksForUnfinishedStatusesOnly() {
        RecordingPort port = new RecordingPort(true);

        boolean exists =
                new ExistsUnfinishedRegisteredWorksService(port).exists(new UnfinishedRegisteredWorksQuery(100L, 7L));

        assertThat(exists).isTrue();
        assertThat(port.organizationId).isEqualTo(new OrganizationId(100L));
        assertThat(port.registrarId).isEqualTo(new MembershipId(7L));
        assertThat(port.statuses)
                .isEqualTo(EnumSet.of(
                        WorkStatus.REGISTERED, WorkStatus.PENDING_ACCEPT, WorkStatus.ACCEPTED, WorkStatus.IN_PROGRESS));
    }

    @Test
    @DisplayName("끝나지 않은 등록 작업이 없으면 없다고 답한다")
    void reportsNoneWhenPortFindsNothing() {
        assertThat(new ExistsUnfinishedRegisteredWorksService(new RecordingPort(false))
                        .exists(new UnfinishedRegisteredWorksQuery(100L, 7L)))
                .isFalse();
    }

    private static final class RecordingPort implements RegisteredWorksPort {

        private final boolean result;
        private OrganizationId organizationId;
        private MembershipId registrarId;
        private Set<WorkStatus> statuses;

        private RecordingPort(boolean result) {
            this.result = result;
        }

        @Override
        public boolean existsRegisteredBy(
                OrganizationId organizationId, MembershipId registrarId, Set<WorkStatus> statuses) {
            this.organizationId = organizationId;
            this.registrarId = registrarId;
            this.statuses = statuses;
            return result;
        }
    }
}
