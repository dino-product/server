package com.orbit.schedule.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.orbit.schedule.application.port.in.command.dto.ReleaseDeletedWorkTypeCommand;
import com.orbit.schedule.application.port.in.command.dto.ReleasedWorkTypeInfo;
import com.orbit.schedule.application.port.out.ClearedWorkType;
import com.orbit.schedule.application.port.out.WorkTypeUsagePort;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.TechnicianId;
import com.orbit.schedule.domain.WorkId;
import com.orbit.schedule.domain.WorkStatus;
import com.orbit.schedule.domain.WorkTypeId;

@DisplayName("삭제된 작업 유형 해제")
class ReleaseDeletedWorkTypeServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-19T00:00:00Z");

    private final RecordingPort port = new RecordingPort();
    private final ReleaseDeletedWorkTypeService service =
            new ReleaseDeletedWorkTypeService(port, Clock.fixed(NOW, ZoneOffset.UTC));

    @AfterEach
    void clearIsolationLevel() {
        TransactionSynchronizationManager.setCurrentTransactionIsolationLevel(null);
    }

    @Test
    @DisplayName("시작 전 상태(등록·배정됨·작업전)의 작업만 지금 시각으로 유형을 비우게 한다")
    void clearsOnlyBeforeStartStatuses() {
        service.release(new ReleaseDeletedWorkTypeCommand(100L, 5L));

        assertThat(port.statuses)
                .containsExactlyInAnyOrder(WorkStatus.REGISTERED, WorkStatus.PENDING_ACCEPT, WorkStatus.ACCEPTED);
        assertThat(port.organizationId).isEqualTo(new OrganizationId(100L));
        assertThat(port.workTypeId).isEqualTo(new WorkTypeId(5L));
        assertThat(port.at).isEqualTo(NOW);
    }

    @Test
    @DisplayName("비운 작업을 식별자 순으로 돌려주고, 대기함 작업의 기사 칸은 담당 기사로 돌려주지 않는다")
    void returnsAssignedTechnicianOnly() {
        port.cleared.add(new ClearedWorkType(new WorkId(9L), WorkStatus.ACCEPTED, new TechnicianId(3L)));
        port.cleared.add(new ClearedWorkType(new WorkId(7L), WorkStatus.REGISTERED, new TechnicianId(4L)));
        port.cleared.add(new ClearedWorkType(new WorkId(8L), WorkStatus.PENDING_ACCEPT, new TechnicianId(3L)));

        assertThat(service.release(new ReleaseDeletedWorkTypeCommand(100L, 5L)))
                .containsExactly(
                        new ReleasedWorkTypeInfo(7L, null),
                        new ReleasedWorkTypeInfo(8L, 3L),
                        new ReleasedWorkTypeInfo(9L, 3L));
    }

    @ParameterizedTest
    @ValueSource(ints = {TransactionDefinition.ISOLATION_REPEATABLE_READ, TransactionDefinition.ISOLATION_SERIALIZABLE})
    @DisplayName("바깥 트랜잭션이 READ COMMITTED보다 높은 격리 수준이면 기다린 뒤 커밋된 작업을 못 보므로 유형을 비우기 전에 거부한다")
    void rejectsOuterTransactionAboveReadCommitted(int isolationLevel) {
        TransactionSynchronizationManager.setCurrentTransactionIsolationLevel(isolationLevel);

        assertThatThrownBy(() -> service.release(new ReleaseDeletedWorkTypeCommand(100L, 5L)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(port.statuses).isNull();
    }

    @Test
    @DisplayName("바깥 트랜잭션이 READ COMMITTED로 지정됐으면 그대로 비운다")
    void acceptsOuterTransactionInReadCommitted() {
        TransactionSynchronizationManager.setCurrentTransactionIsolationLevel(
                TransactionDefinition.ISOLATION_READ_COMMITTED);

        service.release(new ReleaseDeletedWorkTypeCommand(100L, 5L));

        assertThat(port.statuses).isNotNull();
    }

    private static final class RecordingPort implements WorkTypeUsagePort {

        private final List<ClearedWorkType> cleared = new ArrayList<>();
        private OrganizationId organizationId;
        private WorkTypeId workTypeId;
        private Set<WorkStatus> statuses;
        private Instant at;

        @Override
        public Map<WorkStatus, Long> countByStatus(OrganizationId organizationId, WorkTypeId workTypeId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<ClearedWorkType> clearWorkType(
                OrganizationId organizationId, WorkTypeId workTypeId, Set<WorkStatus> statuses, Instant at) {
            this.organizationId = organizationId;
            this.workTypeId = workTypeId;
            this.statuses = statuses;
            this.at = at;
            return cleared;
        }
    }
}
