package com.orbit.schedule.adapter.out.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orbit.schedule.domain.ActualPaymentMethod;
import com.orbit.schedule.domain.AssignmentResult;
import com.orbit.schedule.domain.CompletionReport;
import com.orbit.schedule.domain.CustomerInfo;
import com.orbit.schedule.domain.MembershipId;
import com.orbit.schedule.domain.Money;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.PaymentInfo;
import com.orbit.schedule.domain.PaymentMethod;
import com.orbit.schedule.domain.RejectionReason;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkId;
import com.orbit.schedule.domain.WorkSchedule;
import com.orbit.schedule.domain.WorkStatus;
import com.orbit.schedule.domain.WorkTypeId;

@DisplayName("임시 메모리 작업 저장소")
class InMemoryWorkRepositoryTest {

    private static final OrganizationId ORGANIZATION_ID = new OrganizationId(100L);
    private static final MembershipId REGISTRAR_ID = new MembershipId(1L);
    private static final WorkSchedule FIRST_SCHEDULE =
            new WorkSchedule(new MembershipId(3L), Instant.parse("2026-09-25T01:00:00Z"), Duration.ofHours(2));
    private static final WorkSchedule SECOND_SCHEDULE =
            new WorkSchedule(new MembershipId(4L), Instant.parse("2026-09-25T05:00:00Z"), Duration.ofHours(1));
    private static final Instant NOW = Instant.parse("2026-09-24T01:00:00Z");

    private final InMemoryWorkRepository repository = new InMemoryWorkRepository();

    @Test
    @DisplayName("새 작업마다 서로 다른 식별자를 채워 돌려준다")
    void assignsDistinctIdsToNewWorks() {
        WorkId first = repository.save(newWork("첫 작업")).id().orElseThrow();
        WorkId second = repository.save(newWork("둘째 작업")).id().orElseThrow();

        assertThat(first).isNotEqualTo(second);
        assertThat(repository.findById(first).orElseThrow().name()).isEqualTo("첫 작업");
        assertThat(repository.findById(second).orElseThrow().name()).isEqualTo("둘째 작업");
    }

    @Test
    @DisplayName("모든 필드와 거절·마감·수락 이력을 가진 완료 작업을 그대로 저장·조회한다")
    void roundTripsFullyPopulatedWork() {
        Work work = Work.register(
                ORGANIZATION_ID,
                "에어컨 수리",
                REGISTRAR_ID,
                new WorkTypeId(2L),
                new CustomerInfo("홍길동", "010-1234-5678", "서울시"),
                new PaymentInfo(new Money(150_000L), PaymentMethod.ON_SITE_CARD));
        work.assign(FIRST_SCHEDULE, NOW);
        work.reject(RejectionReason.SCHEDULE_CONFLICT, NOW.plusSeconds(10));
        work.assign(SECOND_SCHEDULE, NOW.plusSeconds(20));
        work.reschedule(
                new WorkSchedule(new MembershipId(4L), Instant.parse("2026-09-25T07:00:00Z"), Duration.ofHours(1)),
                NOW.plusSeconds(30));
        work.accept(NOW.plusSeconds(40));
        work.start();
        work.submitCompletionReport(new CompletionReport(
                List.of("before.jpg"),
                List.of("after.jpg"),
                "필터 1개",
                "교체 완료",
                new Money(150_000L),
                ActualPaymentMethod.CREDIT_CARD));

        WorkId id = repository.save(work).id().orElseThrow();
        Work found = repository.findById(id).orElseThrow();

        assertThat(found).usingRecursiveComparison().ignoringFields("id").isEqualTo(work);
        assertThat(found.status()).isEqualTo(WorkStatus.COMPLETED);
        assertThat(found.assignmentHistory())
                .extracting(history -> history.result())
                .containsExactly(AssignmentResult.REJECTED, AssignmentResult.REASSIGNED, AssignmentResult.ACCEPTED);
    }

    @Test
    @DisplayName("조회한 작업을 저장하지 않고 바꾸면 저장값은 그대로다")
    void doesNotLeakUnsavedChangesOfLoadedWork() {
        WorkId id = repository.save(newWork("에어컨 수리")).id().orElseThrow();
        Work loaded = repository.findById(id).orElseThrow();

        loaded.assign(FIRST_SCHEDULE, NOW);

        assertThat(repository.findById(id).orElseThrow().status()).isEqualTo(WorkStatus.REGISTERED);
    }

    @Test
    @DisplayName("저장한 뒤 넘긴 작업이나 돌려받은 작업을 바꿔도 저장값은 그대로다")
    void doesNotShareInstancesAfterSave() {
        WorkId id = repository.save(newWork("에어컨 수리")).id().orElseThrow();
        Work loaded = repository.findById(id).orElseThrow();
        loaded.assign(FIRST_SCHEDULE, NOW);
        Work returned = repository.save(loaded);

        loaded.accept(NOW.plusSeconds(60));
        returned.accept(NOW.plusSeconds(60));

        Work reloaded = repository.findById(id).orElseThrow();
        assertThat(reloaded.status()).isEqualTo(WorkStatus.PENDING_ACCEPTANCE);
        assertThat(reloaded.assignmentHistory()).singleElement().satisfies(history -> assertThat(history.result())
                .isEqualTo(AssignmentResult.PENDING));
    }

    @Test
    @DisplayName("식별자가 있는데 저장소에 없는 작업은 저장하지 않는다")
    void rejectsSavingUnknownWork() {
        Work unknown = Work.reconstitute(
                new WorkId(999L),
                ORGANIZATION_ID,
                "에어컨 수리",
                REGISTRAR_ID,
                null,
                null,
                null,
                null,
                WorkStatus.REGISTERED,
                List.of(),
                null);

        assertThatThrownBy(() -> repository.save(unknown))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cannot save unknown work: 999");
        assertThat(repository.findById(new WorkId(999L))).isEmpty();
    }

    @Test
    @DisplayName("조직 안에서 기사에게 현재 배정된 활성 작업(수락대기·수락됨·작업중)만 찾는다")
    void findsActiveWorksOfTechnicianInOrganization() {
        MembershipId technician = FIRST_SCHEDULE.technicianId();
        WorkId pending = saveIn(FIRST_SCHEDULE, WorkStatus.PENDING_ACCEPTANCE);
        WorkId accepted = saveIn(FIRST_SCHEDULE, WorkStatus.ACCEPTED);
        WorkId inProgress = saveIn(FIRST_SCHEDULE, WorkStatus.IN_PROGRESS);
        saveIn(FIRST_SCHEDULE, WorkStatus.COMPLETED);
        saveIn(FIRST_SCHEDULE, WorkStatus.CANCELLED);
        saveIn(SECOND_SCHEDULE, WorkStatus.PENDING_ACCEPTANCE);
        repository.save(newWork("미배정"));
        Work otherOrganizationWork = Work.register(new OrganizationId(200L), "다른 조직", REGISTRAR_ID, null, null, null);
        otherOrganizationWork.assign(FIRST_SCHEDULE, NOW);
        repository.save(otherOrganizationWork);

        List<Work> found = repository.findActiveByTechnician(ORGANIZATION_ID, technician);

        assertThat(found)
                .extracting(work -> work.id().orElseThrow())
                .containsExactlyInAnyOrder(pending, accepted, inProgress);
    }

    @Test
    @DisplayName("다른 기사로 재배정된 작업은 이전 기사의 활성 작업에서 빠진다(과거 이력이 아니라 현재 배정 기준)")
    void usesCurrentAssignmentNotHistory() {
        Work work = newWork("재배정 작업");
        work.assign(FIRST_SCHEDULE, NOW);
        work.reassign(SECOND_SCHEDULE, NOW.plusSeconds(10));
        WorkId id = repository.save(work).id().orElseThrow();

        assertThat(repository.findActiveByTechnician(ORGANIZATION_ID, FIRST_SCHEDULE.technicianId()))
                .isEmpty();
        assertThat(repository.findActiveByTechnician(ORGANIZATION_ID, SECOND_SCHEDULE.technicianId()))
                .extracting(found -> found.id().orElseThrow())
                .containsExactly(id);
    }

    @Test
    @DisplayName("찾은 작업을 저장하지 않고 바꿔도 저장값은 그대로다")
    void doesNotLeakUnsavedChangesOfFoundWorks() {
        WorkId id = saveIn(FIRST_SCHEDULE, WorkStatus.PENDING_ACCEPTANCE);
        Work found = repository
                .findActiveByTechnician(ORGANIZATION_ID, FIRST_SCHEDULE.technicianId())
                .getFirst();

        found.accept(NOW.plusSeconds(60));

        assertThat(repository.findById(id).orElseThrow().status()).isEqualTo(WorkStatus.PENDING_ACCEPTANCE);
    }

    @Test
    @DisplayName("없는 작업은 비어 있다")
    void returnsEmptyForUnknownId() {
        assertThat(repository.findById(new WorkId(999L))).isEmpty();
    }

    private WorkId saveIn(WorkSchedule schedule, WorkStatus status) {
        Work work = newWork("작업");
        work.assign(schedule, NOW);
        switch (status) {
            case PENDING_ACCEPTANCE -> {}
            case ACCEPTED -> work.accept(NOW);
            case IN_PROGRESS -> {
                work.accept(NOW);
                work.start();
            }
            case COMPLETED -> {
                work.accept(NOW);
                work.start();
                work.submitCompletionReport(new CompletionReport(null, null, null, null, null, null));
            }
            case CANCELLED -> work.cancel(NOW);
            default -> throw new IllegalArgumentException("unsupported fixture status: " + status);
        }
        return repository.save(work).id().orElseThrow();
    }

    private static Work newWork(String name) {
        return Work.register(ORGANIZATION_ID, name, REGISTRAR_ID, null, null, null);
    }
}
