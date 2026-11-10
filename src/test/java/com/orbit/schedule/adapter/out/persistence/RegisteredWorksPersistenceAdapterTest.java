package com.orbit.schedule.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.orbit.schedule.domain.CancellationReason;
import com.orbit.schedule.domain.CompletionReport;
import com.orbit.schedule.domain.MembershipId;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.TechnicianId;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkSchedule;
import com.orbit.schedule.domain.WorkStatus;
import com.orbit.support.PersistenceAdapterTestSupport;

/** 작업은 {@link WorkPersistenceAdapter}로 저장해 커밋한 뒤 확인한다. */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("소속이 등록한 작업 저장소")
class RegisteredWorksPersistenceAdapterTest extends PersistenceAdapterTestSupport {

    private static final AtomicLong NEXT_ORGANIZATION = new AtomicLong(40_000);
    private static final Instant REGISTERED_AT = Instant.parse("2026-10-01T00:00:00Z");
    private static final Instant NOW = Instant.parse("2026-10-02T00:00:00Z");
    private static final MembershipId REGISTRAR = new MembershipId(1L);
    private static final MembershipId OTHER_REGISTRAR = new MembershipId(2L);
    private static final TechnicianId TECHNICIAN = new TechnicianId(3L);
    private static final Set<WorkStatus> UNFINISHED =
            EnumSet.of(WorkStatus.REGISTERED, WorkStatus.PENDING_ACCEPT, WorkStatus.ACCEPTED, WorkStatus.IN_PROGRESS);

    @Autowired
    private WorkPersistenceAdapter works;

    @Autowired
    private RegisteredWorksPersistenceAdapter registeredWorks;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("그 조직에서 그 소속이 등록한 작업 중 지정한 상태인 작업이 있으면 있다고 답한다")
    void findsWorkOfGivenStatusRegisteredByMember() {
        for (WorkStatus status : UNFINISHED) {
            OrganizationId organization = organization();
            save(organization, REGISTRAR, WorkStatus.COMPLETED);
            save(organization, REGISTRAR, status);

            assertThat(exists(organization, REGISTRAR)).as(status.name()).isTrue();
        }
    }

    @Test
    @DisplayName("지정하지 않은 상태의 작업과 다른 소속·다른 조직이 등록한 작업은 보지 않는다")
    void ignoresOtherStatusesRegistrarsAndOrganizations() {
        OrganizationId organization = organization();
        save(organization, REGISTRAR, WorkStatus.COMPLETED);
        save(organization, REGISTRAR, WorkStatus.CANCELED);
        save(organization, OTHER_REGISTRAR, WorkStatus.REGISTERED);
        save(organization(), REGISTRAR, WorkStatus.REGISTERED);

        assertThat(exists(organization, REGISTRAR)).isFalse();
        assertThat(exists(organization(), REGISTRAR)).isFalse();
    }

    private boolean exists(OrganizationId organization, MembershipId registrar) {
        return inTx(() -> registeredWorks.existsRegisteredBy(organization, registrar, UNFINISHED));
    }

    private void save(OrganizationId organization, MembershipId registrar, WorkStatus status) {
        Work work = Work.register(organization, "작업", registrar, REGISTERED_AT, null, null, null);
        if (status != WorkStatus.REGISTERED && status != WorkStatus.CANCELED) {
            work.assign(new WorkSchedule(TECHNICIAN, NOW.plusSeconds(3_600), Duration.ofHours(1)), NOW, registrar);
        }
        if (status == WorkStatus.ACCEPTED || status == WorkStatus.IN_PROGRESS || status == WorkStatus.COMPLETED) {
            work.accept(NOW.plusSeconds(10));
        }
        if (status == WorkStatus.IN_PROGRESS || status == WorkStatus.COMPLETED) {
            work.start(NOW.plusSeconds(20));
        }
        if (status == WorkStatus.COMPLETED) {
            work.submitCompletionReport(
                    new CompletionReport(List.of(), List.of(), null, null, null, null), NOW.plusSeconds(30));
        }
        if (status == WorkStatus.CANCELED) {
            work.cancel(NOW.plusSeconds(30), registrar, CancellationReason.CUSTOMER_REQUEST, null);
        }
        inTx(() -> works.save(work));
    }

    private <T> T inTx(Supplier<T> action) {
        return new TransactionTemplate(transactionManager).execute(status -> action.get());
    }

    private static OrganizationId organization() {
        return new OrganizationId(NEXT_ORGANIZATION.incrementAndGet());
    }
}
