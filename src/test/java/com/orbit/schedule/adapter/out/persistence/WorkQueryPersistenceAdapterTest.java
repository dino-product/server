package com.orbit.schedule.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
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

import com.orbit.schedule.application.port.out.WorkSearchCriteria;
import com.orbit.schedule.application.port.out.WorkSearchResult;
import com.orbit.schedule.domain.CancellationReason;
import com.orbit.schedule.domain.CompletionReport;
import com.orbit.schedule.domain.CustomerInfo;
import com.orbit.schedule.domain.MembershipId;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.PlannedSchedule;
import com.orbit.schedule.domain.Rejection;
import com.orbit.schedule.domain.RejectionReason;
import com.orbit.schedule.domain.TechnicianId;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkId;
import com.orbit.schedule.domain.WorkSchedule;
import com.orbit.schedule.domain.WorkStatus;
import com.orbit.support.PersistenceTestConfiguration;

/**
 * 실제 PostgreSQL에서 WorkQueryPort 계약(구간 경계, 조직 격리, 대기함의 미리 입력한 일정 제외, 검색 조건·정렬·쪽 나누기, 분리된 사본)을 확인한다. 조직 식별자를
 * 테스트마다 다르게 두어 같은 DB를 쓰는 다른 테스트의 데이터와 섞이지 않게 한다.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PersistenceTestConfiguration.class, WorkPersistenceAdapter.class, WorkQueryPersistenceAdapter.class})
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("작업 조회 JPA 저장소")
class WorkQueryPersistenceAdapterTest {

    private static final Instant REGISTERED_AT = Instant.parse("2020-01-01T00:00:00Z");

    private static final AtomicLong NEXT_ORGANIZATION = new AtomicLong(900_000);
    private static final MembershipId REGISTRAR = new MembershipId(1L);
    private static final MembershipId MANAGER = new MembershipId(99L);
    private static final TechnicianId TECHNICIAN = new TechnicianId(3L);
    private static final TechnicianId OTHER_TECHNICIAN = new TechnicianId(4L);
    private static final Instant FROM = Instant.parse("2026-10-21T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-10-22T00:00:00Z");
    private static final Instant ASSIGNED_AT = Instant.parse("2026-10-20T00:00:00Z");

    @Autowired
    private WorkPersistenceAdapter repository;

    @Autowired
    private WorkQueryPersistenceAdapter adapter;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Nested
    @DisplayName("구간 일정 조회")
    class ScheduledBetween {

        @Test
        @DisplayName("구간과 겹치는 수락대기·수락됨·작업중·완료 작업만 담는다")
        void listsWorksOverlappingPeriodInScheduledStatuses() {
            OrganizationId organization = organization();
            WorkId pending = save(assigned(organization, "수락대기", schedule(TECHNICIAN, "2026-10-21T01:00:00Z", 60)));
            WorkId accepted = save(accepted(organization, "수락됨", schedule(TECHNICIAN, "2026-10-21T03:00:00Z", 60)));
            WorkId inProgress = save(started(organization, "작업중", schedule(TECHNICIAN, "2026-10-21T05:00:00Z", 60)));
            WorkId completed = save(completed(organization, "완료", schedule(TECHNICIAN, "2026-10-21T07:00:00Z", 60)));
            save(cancelled(organization, "취소", schedule(TECHNICIAN, "2026-10-21T09:00:00Z", 60)));
            save(planned(organization, "미리 입력한 일정", schedule(TECHNICIAN, "2026-10-21T11:00:00Z", 60)));
            save(assigned(organization(), "다른 조직", schedule(TECHNICIAN, "2026-10-21T01:00:00Z", 60)));

            List<Work> works = inTx(() -> adapter.listScheduledBetween(organization, FROM, TO));

            assertThat(ids(works)).containsExactlyInAnyOrder(pending, accepted, inProgress, completed);
        }

        @Test
        @DisplayName("구간 앞에서 시작해 구간 안에서 끝나는 작업은 담고 경계에 닿기만 하는 작업은 뺀다")
        void appliesHalfOpenBoundaries() {
            OrganizationId organization = organization();
            WorkId spansFrom =
                    save(assigned(organization, "전날 밤 시작", schedule(TECHNICIAN, "2026-10-20T23:00:00Z", 120)));
            WorkId startedLongBefore =
                    save(assigned(organization, "하루 전 시작", schedule(OTHER_TECHNICIAN, "2026-10-20T00:30:00Z", 1440)));
            save(assigned(organization, "from에 끝남", schedule(TECHNICIAN, "2026-10-20T22:00:00Z", 60)));
            save(assigned(organization, "to에 시작", schedule(TECHNICIAN, "2026-10-22T00:00:00Z", 60)));

            List<Work> works = inTx(() -> adapter.listScheduledBetween(organization, FROM, TO));

            assertThat(ids(works)).containsExactlyInAnyOrder(spansFrom, startedLongBefore);
        }

        @Test
        @DisplayName("기사별 조회는 지금 일정의 담당이 그 기사인 작업만 담고 미리 입력한 담당은 보지 않는다")
        void listsOnlyWorksOfTechnician() {
            OrganizationId organization = organization();
            WorkId mine = save(assigned(organization, "내 작업", schedule(TECHNICIAN, "2026-10-21T01:00:00Z", 60)));
            Work reassigned = assigned(organization, "넘어간 작업", schedule(TECHNICIAN, "2026-10-21T03:00:00Z", 60));
            reassigned.reassign(
                    schedule(OTHER_TECHNICIAN, "2026-10-21T03:00:00Z", 60), ASSIGNED_AT.plusSeconds(60), MANAGER);
            save(reassigned);
            save(planned(organization, "미리 입력한 담당", schedule(TECHNICIAN, "2026-10-21T05:00:00Z", 60)));

            List<Work> works = inTx(() -> adapter.listScheduledForTechnician(organization, TECHNICIAN, FROM, TO));

            assertThat(ids(works)).containsExactly(mine);
        }
    }

    @Nested
    @DisplayName("기사 배정 이력 조회")
    class AssignedToTechnician {

        @Test
        @DisplayName("그 조직에서 그 기사의 배정이 구간과 겹친 작업을 지금 상태와 관계없이 한 번씩 담는다")
        void listsWorksEverAssignedInPeriodOnce() {
            OrganizationId organization = organization();
            WorkId current = save(assigned(organization, "지금 담당", schedule(TECHNICIAN, "2026-10-21T01:00:00Z", 60)));
            Work movedAway = assigned(organization, "넘어간 작업", schedule(TECHNICIAN, "2026-10-21T03:00:00Z", 60));
            movedAway.reassign(
                    schedule(OTHER_TECHNICIAN, "2026-10-21T03:00:00Z", 60), ASSIGNED_AT.plusSeconds(60), MANAGER);
            WorkId movedAwayId = save(movedAway);
            Work rejected = assigned(organization, "거절한 작업", schedule(TECHNICIAN, "2026-10-21T05:00:00Z", 60));
            rejected.reject(new Rejection(RejectionReason.SCHEDULE_CONFLICT, null), ASSIGNED_AT.plusSeconds(60));
            WorkId rejectedId = save(rejected);
            Work rescheduledTwice = assigned(organization, "두 번 겹침", schedule(TECHNICIAN, "2026-10-21T07:00:00Z", 60));
            rescheduledTwice.reschedule(
                    Instant.parse("2026-10-21T08:00:00Z"),
                    Duration.ofMinutes(60),
                    ASSIGNED_AT.plusSeconds(60),
                    MANAGER);
            WorkId twiceId = save(rescheduledTwice);
            Work movedIn = assigned(organization, "옮겨 온 작업", schedule(OTHER_TECHNICIAN, "2026-10-23T01:00:00Z", 60));
            movedIn.reassign(schedule(TECHNICIAN, "2026-10-21T10:00:00Z", 60), ASSIGNED_AT.plusSeconds(60), MANAGER);
            WorkId movedInId = save(movedIn);
            WorkId cancelledId =
                    save(cancelled(organization, "취소된 작업", schedule(TECHNICIAN, "2026-10-21T12:00:00Z", 60)));
            Work otherOnly = assigned(organization, "다른 기사만", schedule(OTHER_TECHNICIAN, "2026-10-21T01:00:00Z", 60));
            save(otherOnly);
            save(assigned(organization, "구간 밖", schedule(TECHNICIAN, "2026-10-23T01:00:00Z", 60)));
            save(assigned(organization(), "다른 조직", schedule(TECHNICIAN, "2026-10-21T01:00:00Z", 60)));

            List<Work> works = inTx(() -> adapter.listAssignedToTechnician(organization, TECHNICIAN, FROM, TO));

            assertThat(ids(works))
                    .containsExactlyInAnyOrder(current, movedAwayId, rejectedId, twiceId, movedInId, cancelledId);
        }

        @Test
        @DisplayName("그 기사의 배정이 구간 밖이거나 경계에 닿기만 하면 다른 기사의 지금 일정이 구간 안이어도 담지 않는다")
        void ignoresWorksWhoseTechnicianAssignmentsAreOutsidePeriod() {
            OrganizationId organization = organization();
            Work movedAway = assigned(organization, "넘어간 작업", schedule(TECHNICIAN, "2026-10-23T01:00:00Z", 60));
            movedAway.reassign(
                    schedule(OTHER_TECHNICIAN, "2026-10-21T01:00:00Z", 60), ASSIGNED_AT.plusSeconds(60), MANAGER);
            save(movedAway);
            Work endedAtFrom = assigned(organization, "from에 끝난 배정", schedule(TECHNICIAN, "2026-10-20T23:00:00Z", 60));
            endedAtFrom.reassign(
                    schedule(OTHER_TECHNICIAN, "2026-10-21T03:00:00Z", 60), ASSIGNED_AT.plusSeconds(60), MANAGER);
            save(endedAtFrom);
            Work startedAtTo = assigned(organization, "to에 시작한 배정", schedule(TECHNICIAN, "2026-10-22T00:00:00Z", 60));
            startedAtTo.reassign(
                    schedule(OTHER_TECHNICIAN, "2026-10-21T05:00:00Z", 60), ASSIGNED_AT.plusSeconds(60), MANAGER);
            save(startedAtTo);

            List<Work> works = inTx(() -> adapter.listAssignedToTechnician(organization, TECHNICIAN, FROM, TO));

            assertThat(works).isEmpty();
        }
    }

    @Test
    @DisplayName("대기함 조회는 미리 입력한 일정이 있어도 등록 상태 작업을 모두 담는다")
    void listsAllRegisteredWorksAsBacklog() {
        OrganizationId organization = organization();
        WorkId plain = save(registered(organization, "빈 작업"));
        WorkId planned = save(planned(organization, "미리 입력", schedule(TECHNICIAN, "2026-10-21T01:00:00Z", 60)));
        Work rejected = assigned(organization, "거절됨", schedule(TECHNICIAN, "2026-10-21T03:00:00Z", 60));
        rejected.reject(new Rejection(RejectionReason.OTHER, "멀어요"), ASSIGNED_AT.plusSeconds(60));
        WorkId rejectedId = save(rejected);
        save(assigned(organization, "배정됨", schedule(TECHNICIAN, "2026-10-21T05:00:00Z", 60)));
        save(cancelledInBacklog(organization, "대기함 취소"));
        save(registered(organization(), "다른 조직"));

        List<Work> works = inTx(() -> adapter.listBacklog(organization));

        assertThat(ids(works)).containsExactlyInAnyOrder(plain, planned, rejectedId);
    }

    @Test
    @DisplayName("조회 결과를 바꿔도 저장하지 않으면 커밋돼도 반영되지 않는다")
    void returnsDetachedCopies() {
        OrganizationId organization = organization();
        WorkId id = save(registered(organization, "원래 이름"));

        inTx(() -> {
            adapter.listBacklog(organization).getFirst().changeDetails("바뀐 이름", null, null, null);
            return null;
        });

        assertThat(inTx(() -> repository.findInOrganization(organization, id))
                        .orElseThrow()
                        .name())
                .isEqualTo("원래 이름");
    }

    @Nested
    @DisplayName("검색")
    class Search {

        @Test
        @DisplayName("검색어는 작업명·고객 이름·연락처·주소를 대소문자 구분 없이 부분 일치로 찾고 와일드카드·이스케이프 문자는 글자로 본다")
        void matchesKeywordLiterally() {
            OrganizationId organization = organization();
            WorkId byName = save(registered(organization, "Boiler 점검", null));
            WorkId byCustomer = save(registered(organization, "작업", new CustomerInfo("BOILER 고객", null, null)));
            WorkId byAddress = save(registered(organization, "작업", new CustomerInfo(null, null, "boiler로 1")));
            WorkId byPhone = save(registered(organization, "작업", new CustomerInfo(null, "010-1234-5678", null)));
            WorkId percent = save(registered(organization, "100% 할인", null));
            save(registered(organization, "1000 할인", null));
            WorkId underscore = save(registered(organization, "A_B 설치", null));
            save(registered(organization, "AXB 설치", null));
            WorkId backslash = save(registered(organization, "C:\\temp\\", null));
            save(registered(organization(), "boiler 다른 조직", null));

            assertThat(searchIds(organization, criteria().keyword("boiler")))
                    .containsExactlyInAnyOrder(byName, byCustomer, byAddress);
            assertThat(searchIds(organization, criteria().keyword("1234-5678"))).containsExactly(byPhone);
            assertThat(searchIds(organization, criteria().keyword("100%"))).containsExactly(percent);
            assertThat(searchIds(organization, criteria().keyword("a_b"))).containsExactly(underscore);
            assertThat(searchIds(organization, criteria().keyword("temp\\"))).containsExactly(backslash);
        }

        @Test
        @DisplayName("상태·담당 기사·시작 구간(시작 포함, 끝 제외)은 완료·취소 작업에 남은 일정까지 거르고 대기함의 미리 입력한 일정은 보지 않는다")
        void filtersByStatusTechnicianAndStartPeriod() {
            OrganizationId organization = organization();
            WorkId pending = save(assigned(organization, "수락대기", schedule(TECHNICIAN, "2026-10-21T01:00:00Z", 60)));
            WorkId otherTechnician =
                    save(assigned(organization, "다른 기사", schedule(OTHER_TECHNICIAN, "2026-10-21T01:00:00Z", 60)));
            WorkId later = save(accepted(organization, "다음 날", schedule(TECHNICIAN, "2026-10-22T01:00:00Z", 60)));
            WorkId planned = save(planned(organization, "미리 입력", schedule(TECHNICIAN, "2026-10-21T03:00:00Z", 60)));
            WorkId completed = save(completed(organization, "완료", schedule(TECHNICIAN, "2026-10-21T00:00:00Z", 60)));
            WorkId cancelled =
                    save(cancelled(organization, "배정 중 취소", schedule(TECHNICIAN, "2026-10-21T05:00:00Z", 60)));
            save(assigned(organization, "to에 시작", schedule(OTHER_TECHNICIAN, "2026-10-22T00:00:00Z", 60)));

            assertThat(searchIds(organization, criteria().statuses(Set.of(WorkStatus.REGISTERED))))
                    .containsExactly(planned);
            assertThat(searchIds(organization, criteria().technician(TECHNICIAN)))
                    .containsExactlyInAnyOrder(pending, later, completed, cancelled);
            assertThat(searchIds(organization, criteria().start(FROM, TO)))
                    .containsExactlyInAnyOrder(pending, otherTechnician, completed, cancelled);
        }

        @Test
        @DisplayName("거절 반환 조건은 마지막 배정이 거절인 대기함 작업만 담는다")
        void filtersReturnedByRejection() {
            OrganizationId organization = organization();
            Work rejected = assigned(organization, "거절됨", schedule(TECHNICIAN, "2026-10-21T01:00:00Z", 60));
            rejected.reject(new Rejection(RejectionReason.SCHEDULE_CONFLICT, null), ASSIGNED_AT.plusSeconds(60));
            WorkId rejectedId = save(rejected);
            Work rejectedThenUnassigned =
                    assigned(organization, "거절 뒤 재배정 해제", schedule(TECHNICIAN, "2026-10-21T03:00:00Z", 60));
            rejectedThenUnassigned.reject(
                    new Rejection(RejectionReason.SCHEDULE_CONFLICT, null), ASSIGNED_AT.plusSeconds(60));
            rejectedThenUnassigned.assign(
                    schedule(OTHER_TECHNICIAN, "2026-10-21T03:00:00Z", 60), ASSIGNED_AT.plusSeconds(120), MANAGER);
            rejectedThenUnassigned.unassign(ASSIGNED_AT.plusSeconds(180), MANAGER);
            save(rejectedThenUnassigned);
            save(registered(organization, "그냥 대기"));

            assertThat(searchIds(organization, criteria().returnedByRejection()))
                    .containsExactly(rejectedId);
        }

        @Test
        @DisplayName("지연 조건은 그 시각에 예정 종료가 지났거나 같은 활성 작업만 담는다")
        void filtersDelayedAtGivenInstant() {
            OrganizationId organization = organization();
            Instant delayedAt = Instant.parse("2026-10-21T02:00:00Z");
            WorkId endsExactly = save(assigned(organization, "딱 끝남", schedule(TECHNICIAN, "2026-10-21T01:00:00Z", 60)));
            WorkId inProgress =
                    save(started(organization, "작업중 지연", schedule(OTHER_TECHNICIAN, "2026-10-21T00:00:00Z", 30)));
            save(assigned(organization, "아직 안 끝남", schedule(TECHNICIAN, "2026-10-21T01:30:00Z", 60)));
            save(completed(organization, "완료", schedule(TECHNICIAN, "2026-10-20T01:00:00Z", 60)));

            assertThat(searchIds(organization, criteria().delayedAt(delayedAt)))
                    .containsExactlyInAnyOrder(endsExactly, inProgress);
        }

        @Test
        @DisplayName("시작시각 정렬은 일정이 없는 작업을 오름·내림차순 모두 맨 뒤에 두고 같은 값은 식별자 내림차순이다")
        void sortsByStartTimeWithUnscheduledLast() {
            OrganizationId organization = organization();
            WorkId early = save(assigned(organization, "이른", schedule(TECHNICIAN, "2026-10-21T01:00:00Z", 60)));
            WorkId late = save(assigned(organization, "늦은", schedule(TECHNICIAN, "2026-10-21T05:00:00Z", 60)));
            WorkId sameAsEarly =
                    save(assigned(organization, "이른과 같은", schedule(OTHER_TECHNICIAN, "2026-10-21T01:00:00Z", 60)));
            WorkId planned = save(planned(organization, "미리 입력", schedule(TECHNICIAN, "2026-10-20T01:00:00Z", 60)));
            WorkId unscheduled = save(registered(organization, "일정 없음"));

            assertThat(searchIds(organization, criteria().sort(WorkSearchCriteria.Sort.START_TIME_ASC)))
                    .containsExactly(sameAsEarly, early, late, unscheduled, planned);
            assertThat(searchIds(organization, criteria().sort(WorkSearchCriteria.Sort.START_TIME_DESC)))
                    .containsExactly(late, sameAsEarly, early, unscheduled, planned);
            assertThat(searchIds(organization, criteria().sort(WorkSearchCriteria.Sort.REGISTERED_DESC)))
                    .containsExactly(unscheduled, planned, sameAsEarly, late, early);
        }

        @Test
        @DisplayName("등록 최신순은 저장 순서가 아니라 작업의 등록 시각으로 정렬한다")
        void sortsByRegisteredAtRatherThanId() {
            OrganizationId organization = organization();
            WorkId registeredLater = save(
                    Work.register(organization, "늦게 등록", REGISTRAR, REGISTERED_AT.plusSeconds(60), null, null, null));
            WorkId registeredEarlier =
                    save(Work.register(organization, "먼저 등록", REGISTRAR, REGISTERED_AT, null, null, null));

            assertThat(searchIds(organization, criteria())).containsExactly(registeredLater, registeredEarlier);
        }

        @Test
        @DisplayName("요청한 쪽만 담고 전체 수는 쪽과 관계없으며 끝을 넘은 쪽은 비어 있다")
        void paginatesWithTotalCount() {
            OrganizationId organization = organization();
            WorkId first = save(registered(organization, "1"));
            WorkId second = save(registered(organization, "2"));
            WorkId third = save(registered(organization, "3"));

            WorkSearchResult firstPage = inTx(
                    () -> adapter.search(organization, criteria().page(0, 2).build()));
            WorkSearchResult secondPage = inTx(
                    () -> adapter.search(organization, criteria().page(1, 2).build()));
            WorkSearchResult beyond = inTx(
                    () -> adapter.search(organization, criteria().page(5, 2).build()));

            assertThat(ids(firstPage.works())).containsExactly(third, second);
            assertThat(ids(secondPage.works())).containsExactly(first);
            assertThat(beyond.works()).isEmpty();
            assertThat(List.of(firstPage.totalCount(), secondPage.totalCount(), beyond.totalCount()))
                    .containsOnly(3L);
        }
    }

    private List<WorkId> searchIds(OrganizationId organization, CriteriaBuilder builder) {
        return ids(inTx(() -> adapter.search(organization, builder.build())).works());
    }

    private static CriteriaBuilder criteria() {
        return new CriteriaBuilder();
    }

    /** 검색 조건을 기본값(조건 없음, 등록 최신순, 첫 쪽 100건)에서 필요한 것만 바꿔 만든다. */
    private static final class CriteriaBuilder {

        private String keyword;
        private Set<WorkStatus> statuses = Set.of();
        private TechnicianId technicianId;
        private Instant startFrom;
        private Instant startTo;
        private boolean returnedByRejectionOnly;
        private Instant delayedAt;
        private WorkSearchCriteria.Sort sort = WorkSearchCriteria.Sort.REGISTERED_DESC;
        private int page;
        private int size = WorkSearchCriteria.MAX_SIZE;

        CriteriaBuilder keyword(String value) {
            keyword = value;
            return this;
        }

        CriteriaBuilder statuses(Set<WorkStatus> value) {
            statuses = value;
            return this;
        }

        CriteriaBuilder technician(TechnicianId value) {
            technicianId = value;
            return this;
        }

        CriteriaBuilder start(Instant from, Instant to) {
            startFrom = from;
            startTo = to;
            return this;
        }

        CriteriaBuilder returnedByRejection() {
            returnedByRejectionOnly = true;
            return this;
        }

        CriteriaBuilder delayedAt(Instant value) {
            delayedAt = value;
            return this;
        }

        CriteriaBuilder sort(WorkSearchCriteria.Sort value) {
            sort = value;
            return this;
        }

        CriteriaBuilder page(int pageNumber, int pageSize) {
            page = pageNumber;
            size = pageSize;
            return this;
        }

        WorkSearchCriteria build() {
            return new WorkSearchCriteria(
                    keyword,
                    statuses,
                    technicianId,
                    startFrom,
                    startTo,
                    returnedByRejectionOnly,
                    delayedAt,
                    sort,
                    page,
                    size);
        }
    }

    private WorkId save(Work work) {
        return inTx(() -> repository.save(work)).id().orElseThrow();
    }

    private <T> T inTx(Supplier<T> action) {
        return new TransactionTemplate(transactionManager).execute(status -> action.get());
    }

    private static List<WorkId> ids(List<Work> works) {
        return works.stream().map(work -> work.id().orElseThrow()).toList();
    }

    private static Work registered(OrganizationId organization, String name) {
        return registered(organization, name, null);
    }

    private static Work registered(OrganizationId organization, String name, CustomerInfo customer) {
        return Work.register(organization, name, REGISTRAR, REGISTERED_AT, null, customer, null);
    }

    private static Work planned(OrganizationId organization, String name, WorkSchedule schedule) {
        return Work.register(
                organization,
                name,
                REGISTRAR,
                REGISTERED_AT,
                null,
                null,
                null,
                // 일부만 입력해야 미리 입력한 일정이다. 예상소요시간을 비워 담당기사·시작시각만 남긴다.
                new PlannedSchedule(schedule.technicianId(), schedule.startTime(), null));
    }

    private static Work assigned(OrganizationId organization, String name, WorkSchedule schedule) {
        Work work = registered(organization, name);
        work.assign(schedule, ASSIGNED_AT, MANAGER);
        return work;
    }

    private static Work accepted(OrganizationId organization, String name, WorkSchedule schedule) {
        Work work = assigned(organization, name, schedule);
        work.accept(ASSIGNED_AT.plusSeconds(10));
        return work;
    }

    private static Work started(OrganizationId organization, String name, WorkSchedule schedule) {
        Work work = accepted(organization, name, schedule);
        work.start(schedule.startTime());
        return work;
    }

    private static Work completed(OrganizationId organization, String name, WorkSchedule schedule) {
        Work work = started(organization, name, schedule);
        work.submitCompletionReport(
                new CompletionReport(List.of(), List.of(), null, null, null, null), schedule.endTime());
        return work;
    }

    private static Work cancelled(OrganizationId organization, String name, WorkSchedule schedule) {
        Work work = assigned(organization, name, schedule);
        work.cancel(ASSIGNED_AT.plusSeconds(30), MANAGER, CancellationReason.CUSTOMER_REQUEST, null);
        return work;
    }

    private static Work cancelledInBacklog(OrganizationId organization, String name) {
        Work work = registered(organization, name);
        work.cancel(ASSIGNED_AT, MANAGER, CancellationReason.DUPLICATE_REGISTRATION, null);
        return work;
    }

    private static WorkSchedule schedule(TechnicianId technician, String start, long minutes) {
        return new WorkSchedule(technician, Instant.parse(start), Duration.ofMinutes(minutes));
    }

    private static OrganizationId organization() {
        return new OrganizationId(NEXT_ORGANIZATION.incrementAndGet());
    }
}
