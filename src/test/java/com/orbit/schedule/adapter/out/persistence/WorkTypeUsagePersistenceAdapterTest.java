package com.orbit.schedule.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.orbit.schedule.application.port.out.ClearedWorkType;
import com.orbit.schedule.domain.CancellationReason;
import com.orbit.schedule.domain.MembershipId;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.TechnicianId;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkId;
import com.orbit.schedule.domain.WorkSchedule;
import com.orbit.schedule.domain.WorkStatus;
import com.orbit.schedule.domain.WorkTypeId;
import com.orbit.support.PersistenceAdapterTestSupport;

/** 작업은 {@link WorkPersistenceAdapter}로 저장하고, 조작마다 트랜잭션을 열어 커밋한 뒤 결과를 다시 읽는다. */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("작업 유형 사용 현황·해제 저장소")
class WorkTypeUsagePersistenceAdapterTest extends PersistenceAdapterTestSupport {

    private static final AtomicLong NEXT_ORGANIZATION = new AtomicLong(30_000);
    private static final Instant REGISTERED_AT = Instant.parse("2026-10-01T00:00:00Z");
    private static final Instant NOW = Instant.parse("2026-10-02T00:00:00Z");
    private static final Instant CLEARED_AT = Instant.parse("2026-10-03T00:00:00.123456Z");
    private static final MembershipId MANAGER = new MembershipId(1L);
    private static final TechnicianId TECHNICIAN = new TechnicianId(3L);
    private static final WorkTypeId TYPE = new WorkTypeId(5L);
    private static final WorkTypeId OTHER_TYPE = new WorkTypeId(6L);

    @Autowired
    private WorkPersistenceAdapter works;

    @Autowired
    private WorkTypeUsagePersistenceAdapter usage;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("그 조직에서 그 유형을 쓰는 작업 수를 상태별로 센다")
    void countsWorksByStatus() {
        OrganizationId organization = organization();
        save(organization, TYPE, WorkStatus.REGISTERED);
        save(organization, TYPE, WorkStatus.REGISTERED);
        save(organization, TYPE, WorkStatus.ACCEPTED);
        save(organization, TYPE, WorkStatus.CANCELED);
        save(organization, OTHER_TYPE, WorkStatus.REGISTERED);
        save(organization(), TYPE, WorkStatus.REGISTERED);

        assertThat(inTx(() -> usage.countByStatus(organization, TYPE)))
                .isEqualTo(Map.of(WorkStatus.REGISTERED, 2L, WorkStatus.ACCEPTED, 1L, WorkStatus.CANCELED, 1L));
    }

    @Test
    @DisplayName("지정한 상태의 작업만 유형을 비우고 버전을 올리며, 다른 상태·유형·조직의 작업은 그대로 둔다")
    void clearsWorkTypeOnlyOfGivenStatuses() {
        OrganizationId organization = organization();
        Work registered = save(organization, TYPE, WorkStatus.REGISTERED);
        Work pending = save(organization, TYPE, WorkStatus.PENDING_ACCEPT);
        Work accepted = save(organization, TYPE, WorkStatus.ACCEPTED);
        Work inProgress = save(organization, TYPE, WorkStatus.IN_PROGRESS);
        Work cancelled = save(organization, TYPE, WorkStatus.CANCELED);
        Work otherType = save(organization, OTHER_TYPE, WorkStatus.REGISTERED);
        OrganizationId otherOrganization = organization();
        Work otherOrganizationWork = save(otherOrganization, TYPE, WorkStatus.REGISTERED);

        List<ClearedWorkType> cleared = inTx(() -> usage.clearWorkType(
                organization,
                TYPE,
                EnumSet.of(WorkStatus.REGISTERED, WorkStatus.PENDING_ACCEPT, WorkStatus.ACCEPTED),
                CLEARED_AT));

        assertThat(cleared)
                .containsExactlyInAnyOrder(
                        new ClearedWorkType(id(registered), WorkStatus.REGISTERED, null),
                        new ClearedWorkType(id(pending), WorkStatus.PENDING_ACCEPT, TECHNICIAN),
                        new ClearedWorkType(id(accepted), WorkStatus.ACCEPTED, TECHNICIAN));
        for (Work work : List.of(registered, pending, accepted)) {
            Work found = find(organization, work);
            assertThat(found.workType()).isEmpty();
            assertThat(found.version()).isEqualTo(work.version() + 1);
        }
        assertThat(find(organization, inProgress).workType()).contains(TYPE);
        assertThat(find(organization, cancelled).workType()).contains(TYPE);
        assertThat(find(organization, otherType).workType()).contains(OTHER_TYPE);
        assertThat(find(otherOrganization, otherOrganizationWork).workType()).contains(TYPE);
    }

    private Work save(OrganizationId organization, WorkTypeId workType, WorkStatus status) {
        Work work = Work.register(organization, "작업", MANAGER, REGISTERED_AT, workType, null, null);
        if (status != WorkStatus.REGISTERED) {
            work.assign(new WorkSchedule(TECHNICIAN, NOW.plusSeconds(3_600), Duration.ofHours(1)), NOW, MANAGER);
        }
        if (status == WorkStatus.ACCEPTED || status == WorkStatus.IN_PROGRESS) {
            work.accept(NOW.plusSeconds(10));
        }
        if (status == WorkStatus.IN_PROGRESS) {
            work.start(NOW.plusSeconds(20));
        }
        if (status == WorkStatus.CANCELED) {
            work.cancel(NOW.plusSeconds(30), MANAGER, CancellationReason.CUSTOMER_REQUEST, null);
        }
        return inTx(() -> works.save(work));
    }

    private Work find(OrganizationId organization, Work work) {
        return inTx(() -> works.findInOrganization(organization, id(work)).orElseThrow());
    }

    private static WorkId id(Work work) {
        return work.id().orElseThrow();
    }

    private <T> T inTx(Supplier<T> action) {
        return new TransactionTemplate(transactionManager).execute(status -> action.get());
    }

    private static OrganizationId organization() {
        return new OrganizationId(NEXT_ORGANIZATION.incrementAndGet());
    }
}
