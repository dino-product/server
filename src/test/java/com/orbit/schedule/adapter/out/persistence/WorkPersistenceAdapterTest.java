package com.orbit.schedule.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.orbit.schedule.domain.ActualPaymentMethod;
import com.orbit.schedule.domain.AssignmentResult;
import com.orbit.schedule.domain.CompletionReport;
import com.orbit.schedule.domain.CustomerInfo;
import com.orbit.schedule.domain.MembershipId;
import com.orbit.schedule.domain.Money;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.PaymentInfo;
import com.orbit.schedule.domain.PaymentMethod;
import com.orbit.schedule.domain.Rejection;
import com.orbit.schedule.domain.RejectionReason;
import com.orbit.schedule.domain.TechnicianId;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkId;
import com.orbit.schedule.domain.WorkSchedule;
import com.orbit.schedule.domain.WorkStatus;
import com.orbit.schedule.domain.WorkTypeId;
import com.orbit.support.PersistenceTestConfiguration;

/**
 * 실제 PostgreSQL에 저장·복원하며 WorkRepository 계약을 확인한다. 클래스 트랜잭션을 끄고 조작마다 트랜잭션을 열어 커밋하므로, "save하지 않은 변경은 커밋돼도 저장되지
 * 않는다" 같은 계약을 실제 커밋 경계에서 검증한다. 조직 식별자를 테스트마다 다르게 두어 같은 DB를 쓰는 다른 테스트의 데이터와 섞이지 않게 한다.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PersistenceTestConfiguration.class, WorkPersistenceAdapter.class})
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("작업 JPA 저장소")
class WorkPersistenceAdapterTest {

    private static final MembershipId REGISTRAR = new MembershipId(1L);
    private static final MembershipId MANAGER = new MembershipId(99L);
    private static final TechnicianId TECHNICIAN = new TechnicianId(3L);
    private static final TechnicianId OTHER_TECHNICIAN = new TechnicianId(4L);
    // 나노초가 있는 시각으로 저장해 마이크로초로 잘린 값이 그대로 복원되는지 함께 본다.
    private static final Instant NOW = Instant.parse("2026-09-24T01:00:00.123456789Z");
    private static final WorkSchedule SCHEDULE = new WorkSchedule(
            TECHNICIAN,
            Instant.parse("2026-09-25T01:00:00.654321987Z"),
            Duration.ofMinutes(90).plusNanos(123_456_000));
    private static final WorkSchedule OTHER_SCHEDULE =
            new WorkSchedule(OTHER_TECHNICIAN, Instant.parse("2026-09-25T05:00:00Z"), Duration.ofHours(1));

    @Autowired
    private SpringDataWorkRepository springDataRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    // 트랜잭션 프록시가 적용된 Bean을 쓴다. 직접 new 하면 @Transactional이 동작하지 않아 지연 로딩이 세션 밖에서 실패한다.
    @Autowired
    private WorkPersistenceAdapter adapter;

    private WorkPersistenceAdapter adapter() {
        return adapter;
    }

    @Test
    @DisplayName("새 작업마다 서로 다른 식별자를 채워 돌려준다")
    void assignsDistinctIdsToNewWorks() {
        OrganizationId organization = organization();
        WorkId first = inTx(() -> adapter().save(registered(organization, "첫 작업")))
                .id()
                .orElseThrow();
        WorkId second = inTx(() -> adapter().save(registered(organization, "둘째 작업")))
                .id()
                .orElseThrow();

        assertThat(first).isNotEqualTo(second);
        assertThat(find(organization, first).name()).isEqualTo("첫 작업");
        assertThat(find(organization, second).name()).isEqualTo("둘째 작업");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("worksInEveryStatus")
    @DisplayName("모든 상태의 작업이 저장 뒤 복원해도 같다")
    void roundTripsWorkInEveryStatus(String name, Consumer<Work> arrange, WorkStatus expectedStatus) {
        OrganizationId organization = organization();
        Work work = registered(organization, name);
        arrange.accept(work);

        WorkId id = inTx(() -> adapter().save(work)).id().orElseThrow();
        Work found = find(organization, id);

        assertThat(found.status()).isEqualTo(expectedStatus);
        assertThat(found).usingRecursiveComparison().ignoringFields("id").isEqualTo(work);
    }

    static Stream<Arguments> worksInEveryStatus() {
        return Stream.of(
                Arguments.of("대기함(등록)", (Consumer<Work>) work -> {}, WorkStatus.REGISTERED),
                Arguments.of(
                        "배정됨",
                        (Consumer<Work>) work -> work.assign(SCHEDULE, NOW, MANAGER),
                        WorkStatus.PENDING_ACCEPTANCE),
                Arguments.of(
                        "작업전(수락)",
                        (Consumer<Work>) work -> {
                            work.assign(SCHEDULE, NOW, MANAGER);
                            work.accept(NOW.plusSeconds(10));
                        },
                        WorkStatus.ACCEPTED),
                Arguments.of(
                        "작업중",
                        (Consumer<Work>) work -> {
                            work.assign(SCHEDULE, NOW, MANAGER);
                            work.accept(NOW.plusSeconds(10));
                            work.start(NOW.plusSeconds(20));
                        },
                        WorkStatus.IN_PROGRESS),
                Arguments.of(
                        "완료(거절·회수·재배정 이력과 사진 포함)",
                        (Consumer<Work>) WorkPersistenceAdapterTest::completeWithHistory,
                        WorkStatus.COMPLETED),
                Arguments.of(
                        "완료(빈 완료보고)",
                        (Consumer<Work>) work -> {
                            work.assign(SCHEDULE, NOW, MANAGER);
                            work.accept(NOW.plusSeconds(10));
                            work.start(NOW.plusSeconds(20));
                            work.submitCompletionReport(
                                    new CompletionReport(List.of(), List.of(), null, null, null, null),
                                    NOW.plusSeconds(30));
                        },
                        WorkStatus.COMPLETED),
                Arguments.of(
                        "취소(대기함에서)", (Consumer<Work>) work -> work.cancel(NOW, MANAGER, "고객 요청"), WorkStatus.CANCELLED),
                Arguments.of(
                        "취소(작업중에서)",
                        (Consumer<Work>) work -> {
                            work.assign(SCHEDULE, NOW, MANAGER);
                            work.accept(NOW.plusSeconds(10));
                            work.start(NOW.plusSeconds(20));
                            work.cancel(NOW.plusSeconds(30), MANAGER, "기사 사정");
                        },
                        WorkStatus.CANCELLED));
    }

    private static void completeWithHistory(Work work) {
        work.assign(SCHEDULE, NOW, MANAGER);
        work.reject(new Rejection(RejectionReason.OTHER, "거리 멀어요"), NOW.plusSeconds(10));
        work.assign(OTHER_SCHEDULE, NOW.plusSeconds(20), MANAGER);
        work.reschedule(Instant.parse("2026-09-25T07:00:00Z"), Duration.ofHours(1), NOW.plusSeconds(30), MANAGER);
        work.accept(NOW.plusSeconds(40));
        work.reassign(SCHEDULE, NOW.plusSeconds(50), MANAGER);
        work.accept(NOW.plusSeconds(60));
        work.start(NOW.plusSeconds(70));
        work.submitCompletionReport(
                new CompletionReport(
                        List.of("before-1", "before-2"),
                        List.of("after-1"),
                        "필터 1개",
                        "교체 완료",
                        new Money(150_000L),
                        ActualPaymentMethod.CREDIT_CARD),
                NOW.plusSeconds(80));
    }

    @Test
    @DisplayName("같은 시각에 생긴 배정 이력도 순서대로 복원한다")
    void preservesAssignmentOrderWhenTimestampsCollide() {
        OrganizationId organization = organization();
        Work work = registered(organization, "에어컨 수리");
        work.assign(SCHEDULE, NOW, MANAGER);
        work.reject(new Rejection(RejectionReason.SCHEDULE_CONFLICT, null), NOW);
        work.assign(OTHER_SCHEDULE, NOW, MANAGER);
        work.reject(new Rejection(RejectionReason.LOCATION_TOO_FAR, null), NOW);
        work.assign(SCHEDULE, NOW, MANAGER);

        WorkId id = inTx(() -> adapter().save(work)).id().orElseThrow();
        Work found = find(organization, id);

        assertThat(found.assignmentHistory())
                .extracting(history -> history.schedule().technicianId(), history -> history.result())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(TECHNICIAN, AssignmentResult.REJECTED),
                        org.assertj.core.groups.Tuple.tuple(OTHER_TECHNICIAN, AssignmentResult.REJECTED),
                        org.assertj.core.groups.Tuple.tuple(TECHNICIAN, AssignmentResult.PENDING));
        assertThat(found.currentAssignmentNumber()).isEqualTo(3);
    }

    @Test
    @DisplayName("저장 뒤 이어진 변경(수락·시작·완료보고)도 같은 행을 갱신해 복원한다")
    void updatesStoredWorkInPlace() {
        OrganizationId organization = organization();
        Work saved = inTx(() -> adapter().save(registered(organization, "보일러 점검")));
        WorkId id = saved.id().orElseThrow();

        inTx(() -> {
            Work loaded = find(organization, id);
            loaded.assign(SCHEDULE, NOW, MANAGER);
            return adapter().save(loaded);
        });
        inTx(() -> {
            Work loaded = find(organization, id);
            loaded.accept(NOW.plusSeconds(10));
            loaded.start(NOW.plusSeconds(20));
            loaded.submitCompletionReport(
                    new CompletionReport(List.of("b"), List.of("a1", "a2"), null, "메모", null, null),
                    NOW.plusSeconds(30));
            return adapter().save(loaded);
        });

        Work found = find(organization, id);
        assertThat(found.status()).isEqualTo(WorkStatus.COMPLETED);
        assertThat(found.assignmentHistory()).hasSize(1);
        assertThat(found.assignmentHistory().getFirst().result()).isEqualTo(AssignmentResult.ACCEPTED);
        assertThat(found.completionReport().orElseThrow().afterPhotos()).containsExactly("a1", "a2");
        assertThat(inTx(() -> springDataRepository
                        .findById(id.value())
                        .orElseThrow()
                        .assignments()
                        .size()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("다른 조직의 작업은 없는 것처럼 보인다")
    void hidesWorkOfOtherOrganization() {
        OrganizationId organization = organization();
        WorkId id = inTx(() -> adapter().save(registered(organization, "에어컨 수리")))
                .id()
                .orElseThrow();

        assertThat(inTx(() -> adapter().findInOrganization(organization(), id))).isEmpty();
        assertThat(inTx(() -> adapter().listActiveByTechnician(organization(), TECHNICIAN)))
                .isEmpty();
    }

    @Test
    @DisplayName("식별자가 있는데 저장소에 없는 작업은 저장하지 않는다")
    void rejectsSavingUnknownWork() {
        Work unknown = Work.reconstitute(
                new WorkId(Long.MAX_VALUE),
                organization(),
                "없는 작업",
                REGISTRAR,
                null,
                null,
                null,
                null,
                WorkStatus.REGISTERED,
                List.of(),
                null,
                null,
                null,
                null,
                List.of());

        assertThatThrownBy(() -> inTx(() -> adapter().save(unknown)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unknown work");
    }

    @Test
    @DisplayName("다른 조직에 있는 작업은 식별자가 맞아도 저장하지 않는다")
    void rejectsSavingWorkOfOtherOrganization() {
        Work saved = inTx(() -> adapter().save(registered(organization(), "에어컨 수리")));
        Work sameIdOtherOrganization = Work.reconstitute(
                saved.id().orElseThrow(),
                organization(),
                saved.name(),
                REGISTRAR,
                null,
                null,
                null,
                null,
                WorkStatus.REGISTERED,
                List.of(),
                null,
                null,
                null,
                null,
                List.of());

        assertThatThrownBy(() -> inTx(() -> adapter().save(sameIdOtherOrganization)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unknown work");
    }

    @Test
    @DisplayName("서비스 트랜잭션 밖에서 불러도 자체 트랜잭션으로 저장·조회한다")
    void worksOutsideCallerTransaction() {
        OrganizationId organization = organization();
        Work saved = adapter().save(registered(organization, "에어컨 수리"));
        WorkId id = saved.id().orElseThrow();
        saved.assign(SCHEDULE, NOW, MANAGER);

        adapter().save(saved);

        Work found = adapter().findInOrganization(organization, id).orElseThrow();
        assertThat(found.status()).isEqualTo(WorkStatus.PENDING_ACCEPTANCE);
        assertThat(found.assignmentHistory()).hasSize(1);
    }

    @Test
    @DisplayName("상태 정정 기록이 있는 작업은 저장할 자리가 없어 거부한다")
    void rejectsWorkWithStatusCorrections() {
        OrganizationId organization = organization();
        Work work = registered(organization, "정정된 작업");
        work.assign(SCHEDULE, NOW, MANAGER);
        work.cancel(NOW.plusSeconds(10), MANAGER, "중복 등록");
        work.correctStatus(WorkStatus.REGISTERED, NOW.plusSeconds(20), MANAGER, "잘못 취소");

        assertThatThrownBy(() -> inTx(() -> adapter().save(work)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("S-11");
    }

    @Test
    @DisplayName("조회한 작업을 저장하지 않고 바꾼 채 커밋해도 저장값은 그대로다")
    void doesNotPersistUnsavedChangesEvenAfterCommit() {
        OrganizationId organization = organization();
        WorkId id = inTx(() -> adapter().save(registered(organization, "에어컨 수리")))
                .id()
                .orElseThrow();

        inTx(() -> {
            Work loaded = find(organization, id);
            loaded.assign(SCHEDULE, NOW, MANAGER);
            return loaded;
        });

        assertThat(find(organization, id).status()).isEqualTo(WorkStatus.REGISTERED);
    }

    @Test
    @DisplayName("저장한 뒤 넘긴 작업이나 돌려받은 작업을 바꿔도 저장값은 그대로다")
    void doesNotShareInstancesAfterSave() {
        OrganizationId organization = organization();
        WorkId id = inTx(() -> adapter().save(registered(organization, "에어컨 수리")))
                .id()
                .orElseThrow();

        inTx(() -> {
            Work loaded = find(organization, id);
            loaded.assign(SCHEDULE, NOW, MANAGER);
            Work returned = adapter().save(loaded);
            loaded.accept(NOW.plusSeconds(60));
            returned.accept(NOW.plusSeconds(60));
            return returned;
        });

        Work reloaded = find(organization, id);
        assertThat(reloaded.status()).isEqualTo(WorkStatus.PENDING_ACCEPTANCE);
        assertThat(reloaded.assignmentHistory()).singleElement().satisfies(history -> assertThat(history.result())
                .isEqualTo(AssignmentResult.PENDING));
    }

    @Test
    @DisplayName("기사의 활성 작업은 현재 일정이 그 기사이고 상태가 배정됨·작업전·작업중인 같은 조직 작업만이다")
    void listsOnlyActiveWorksCurrentlyAssignedToTechnician() {
        OrganizationId organization = organization();
        Work pending = registered(organization, "배정됨");
        pending.assign(SCHEDULE, NOW, MANAGER);
        Work accepted = registered(organization, "작업전");
        accepted.assign(SCHEDULE, NOW, MANAGER);
        accepted.accept(NOW.plusSeconds(10));
        Work inProgress = registered(organization, "작업중");
        inProgress.assign(SCHEDULE, NOW, MANAGER);
        inProgress.accept(NOW.plusSeconds(10));
        inProgress.start(NOW.plusSeconds(20));
        Work reassignedAway = registered(organization, "다른 기사로 재배정");
        reassignedAway.assign(SCHEDULE, NOW, MANAGER);
        reassignedAway.reassign(OTHER_SCHEDULE, NOW.plusSeconds(10), MANAGER);
        Work cancelled = registered(organization, "취소");
        cancelled.assign(SCHEDULE, NOW, MANAGER);
        cancelled.cancel(NOW.plusSeconds(10), MANAGER, "중복 등록");
        Work backlog = registered(organization, "대기함");
        Work otherOrganization = registered(organization(), "다른 조직");
        otherOrganization.assign(SCHEDULE, NOW, MANAGER);
        for (Work work :
                List.of(pending, accepted, inProgress, reassignedAway, cancelled, backlog, otherOrganization)) {
            inTx(() -> adapter().save(work));
        }

        List<Work> forTechnician = inTx(() -> adapter().listActiveByTechnician(organization, TECHNICIAN));
        List<Work> forOther = inTx(() -> adapter().listActiveByTechnician(organization, OTHER_TECHNICIAN));

        assertThat(forTechnician).extracting(Work::name).containsExactlyInAnyOrder("배정됨", "작업전", "작업중");
        assertThat(forOther).extracting(Work::name).containsExactly("다른 기사로 재배정");
    }

    @Test
    @DisplayName("작업과 자식 행의 감사 컬럼이 채워진다")
    void fillsAuditColumnsOnWorkAndChildren() {
        OrganizationId organization = organization();
        Work work = registered(organization, "에어컨 수리");
        completeWithHistory(work);
        WorkId id = inTx(() -> adapter().save(work)).id().orElseThrow();

        inTx(() -> {
            WorkJpaEntity entity = springDataRepository.findById(id.value()).orElseThrow();
            assertThat(entity.createdAt()).isNotNull();
            assertThat(entity.updatedAt()).isNotNull();
            assertThat(entity.assignments())
                    .allSatisfy(row -> assertThat(row.createdAt()).isNotNull());
            assertThat(entity.images())
                    .allSatisfy(row -> assertThat(row.createdAt()).isNotNull());
            return null;
        });
    }

    private Work find(OrganizationId organization, WorkId id) {
        return inTx(() -> adapter().findInOrganization(organization, id).orElseThrow());
    }

    private <T> T inTx(Supplier<T> action) {
        return new TransactionTemplate(transactionManager).execute(status -> action.get());
    }

    private static Work registered(OrganizationId organization, String name) {
        return Work.register(
                organization,
                name,
                REGISTRAR,
                new WorkTypeId(2L),
                new CustomerInfo("홍길동", "010-1234-5678", "서울시"),
                new PaymentInfo(new Money(150_000L), PaymentMethod.ON_SITE_CARD));
    }

    private static final java.util.concurrent.atomic.AtomicLong NEXT_ORGANIZATION =
            new java.util.concurrent.atomic.AtomicLong(10_000);

    /** 테스트마다 새 조직을 써서 같은 DB를 쓰는 다른 테스트·앞선 테스트의 작업과 섞이지 않게 한다. */
    private static OrganizationId organization() {
        return new OrganizationId(NEXT_ORGANIZATION.incrementAndGet());
    }
}
