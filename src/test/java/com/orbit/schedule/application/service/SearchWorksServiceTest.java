package com.orbit.schedule.application.service;

import static com.orbit.schedule.application.service.ScheduleServiceFixture.ACCEPTED_AT;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.ACCOUNT_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.NOW;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.ORGANIZATION_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.REGISTERED_AT;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.REGISTRAR_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TECHNICIAN_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TEN;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TWO_HOURS;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.WORK_TYPE_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.application.port.in.query.dto.LabelInfo;
import com.orbit.schedule.application.port.in.query.dto.SearchWorksQuery;
import com.orbit.schedule.application.port.in.query.dto.StaffInfo;
import com.orbit.schedule.application.port.in.query.dto.TechnicianInfo;
import com.orbit.schedule.application.port.in.query.dto.WorkSearchFilter;
import com.orbit.schedule.application.port.in.query.dto.WorkSearchInfo;
import com.orbit.schedule.application.port.out.StaffProfile;
import com.orbit.schedule.application.port.out.TechnicianProfile;
import com.orbit.schedule.application.port.out.TypeLabel;
import com.orbit.schedule.application.port.out.WorkSearchCriteria;
import com.orbit.schedule.domain.MembershipId;
import com.orbit.schedule.domain.Rejection;
import com.orbit.schedule.domain.RejectionReason;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkId;
import com.orbit.schedule.domain.WorkStatus;
import com.orbit.schedule.domain.WorkTypeId;

@DisplayName("작업 목록 검색")
class SearchWorksServiceTest {

    private final ScheduleServiceFixture fixture = new ScheduleServiceFixture();
    private final SearchWorksService service = new SearchWorksService(
            fixture.actorPort,
            fixture.workRepository,
            fixture.technicianProfiles,
            fixture.staffProfiles,
            fixture.workTypeLabels,
            fixture.clock);

    @Test
    @DisplayName("비운 조건은 기본값(모든 상태, 최근 등록 먼저, 0쪽, 20건)으로 검색한다")
    void searchesWithDefaults() {
        fixture.givenManager();

        WorkSearchInfo info = service.search(query().build());

        assertThat(fixture.workRepository.searches())
                .containsExactly(new WorkSearchCriteria(
                        null,
                        Set.of(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        false,
                        null,
                        WorkSearchCriteria.Sort.REGISTERED_DESC,
                        0,
                        20));
        assertThat(info.page()).isZero();
        assertThat(info.size()).isEqualTo(20);
    }

    @Test
    @DisplayName("검색어는 앞뒤 공백을 빼고, 식별자는 값객체로, 지연만 보기는 지금 시각으로 판정하도록 조건을 넘긴다")
    void passesNormalizedCriteria() {
        fixture.givenManager();
        Instant to = TEN.plus(Duration.ofDays(7));

        service.search(query().keyword("  보일러  ")
                .statuses(Set.of(WorkStatus.ACCEPTED, WorkStatus.IN_PROGRESS))
                .technicianId(TECHNICIAN_ID.value())
                .workTypeId(WORK_TYPE_ID.value())
                .registrarId(REGISTRAR_ID.value())
                .start(TEN, to)
                .returnedByRejectionOnly()
                .delayedOnly()
                .sort(WorkSearchFilter.Sort.LAST_MODIFIED_DESC)
                .page(2, 50)
                .build());
        service.search(query().keyword("   ")
                .sort(WorkSearchFilter.Sort.REGISTERED_ASC)
                .page(0, 1)
                .build());
        service.search(query().sort(WorkSearchFilter.Sort.START_TIME_ASC).build());

        assertThat(fixture.workRepository.searches())
                .containsExactly(
                        new WorkSearchCriteria(
                                "보일러",
                                Set.of(WorkStatus.ACCEPTED, WorkStatus.IN_PROGRESS),
                                TECHNICIAN_ID,
                                WORK_TYPE_ID,
                                REGISTRAR_ID,
                                TEN,
                                to,
                                true,
                                NOW,
                                WorkSearchCriteria.Sort.LAST_MODIFIED_DESC,
                                2,
                                50),
                        new WorkSearchCriteria(
                                null,
                                Set.of(),
                                null,
                                null,
                                null,
                                null,
                                null,
                                false,
                                null,
                                WorkSearchCriteria.Sort.REGISTERED_ASC,
                                0,
                                1),
                        new WorkSearchCriteria(
                                null,
                                Set.of(),
                                null,
                                null,
                                null,
                                null,
                                null,
                                false,
                                null,
                                WorkSearchCriteria.Sort.START_TIME_ASC,
                                0,
                                20));
    }

    @Test
    @DisplayName("저장소가 돌려준 순서·전체 수 그대로 담고, 일정·표시 정보·등록·수정 시각·지연·거절 반환을 표시한다")
    void mapsResult() {
        fixture.givenManager();
        TypeLabel install = new TypeLabel(WORK_TYPE_ID.value(), "설치", "#7C5CFF", false);
        fixture.workTypeLabels.given(ORGANIZATION_ID, install);
        fixture.technicianProfiles.given(ORGANIZATION_ID, new TechnicianProfile(TECHNICIAN_ID, "김기사", null));
        fixture.staffProfiles.given(ORGANIZATION_ID, new StaffProfile(REGISTRAR_ID, "김운영", false, null));
        WorkId late = fixture.givenWork(
                ORGANIZATION_ID, "늦은 작업", WorkStatus.IN_PROGRESS, TECHNICIAN_ID, NOW.minus(Duration.ofHours(3)));
        Work rejected = fixture.stored(fixture.givenWork(WorkStatus.PENDING_ACCEPTANCE));
        rejected.reject(new Rejection(RejectionReason.SCOPE_MISMATCH, null), ACCEPTED_AT);
        WorkId rejectedId = fixture.workRepository.store(rejected);
        Instant lateModified = NOW.minusSeconds(30);
        Instant rejectedModified = NOW.minusSeconds(10);
        fixture.workRepository.givenSearchResult(
                List.of(late, rejectedId), List.of(lateModified, rejectedModified), 50);

        WorkSearchInfo info = service.search(query().build());

        LabelInfo workType = new LabelInfo(install.id(), "설치", "#7C5CFF");
        StaffInfo registrar = new StaffInfo(REGISTRAR_ID.value(), "김운영", false, null);
        assertThat(info.totalCount()).isEqualTo(50);
        assertThat(info.works())
                .containsExactly(
                        new WorkSearchInfo.WorkSummary(
                                late.value(),
                                "늦은 작업",
                                WorkStatus.IN_PROGRESS,
                                new TechnicianInfo(TECHNICIAN_ID.value(), "김기사", null),
                                NOW.minus(Duration.ofHours(3)),
                                NOW.minus(Duration.ofHours(3)).plus(TWO_HOURS),
                                "홍길동",
                                workType,
                                registrar,
                                REGISTERED_AT,
                                lateModified,
                                true,
                                false),
                        new WorkSearchInfo.WorkSummary(
                                rejectedId.value(),
                                "대상 작업",
                                WorkStatus.REGISTERED,
                                null,
                                null,
                                null,
                                "홍길동",
                                workType,
                                registrar,
                                REGISTERED_AT,
                                rejectedModified,
                                false,
                                true));
    }

    @Test
    @DisplayName("검색어가 100자를 넘거나, 기사·작업유형·등록자·쪽·크기·시작 구간·상태 형식이 틀리면 입력 오류다")
    void rejectsInvalidCriteria() {
        fixture.givenManager();
        Set<WorkStatus> withNull = new HashSet<>();
        withNull.add(null);

        fixture.assertRejected(
                () -> service.search(query().keyword("가".repeat(101)).build()), ScheduleErrorCode.INVALID_WORK_INPUT);
        fixture.assertRejected(
                () -> service.search(query().technicianId(0L).build()), ScheduleErrorCode.INVALID_WORK_INPUT);
        fixture.assertRejected(
                () -> service.search(query().workTypeId(0L).build()), ScheduleErrorCode.INVALID_WORK_INPUT);
        fixture.assertRejected(
                () -> service.search(query().registrarId(-1L).build()), ScheduleErrorCode.INVALID_WORK_INPUT);
        fixture.assertRejected(
                () -> service.search(query().statuses(withNull).build()), ScheduleErrorCode.INVALID_WORK_INPUT);
        for (Instant[] period : new Instant[][] {
            {TEN, null},
            {null, TEN},
            {TEN, TEN},
            {TEN.plusSeconds(1), TEN},
            {Instant.MIN, TEN},
            {TEN, Instant.MAX}
        }) {
            fixture.assertRejected(
                    () -> service.search(query().start(period[0], period[1]).build()),
                    ScheduleErrorCode.INVALID_WORK_INPUT);
        }
        for (int[] paging : new int[][] {{-1, 20}, {0, 0}, {0, 101}, {101, 100}, {Integer.MAX_VALUE, 100}}) {
            fixture.assertRejected(
                    () -> service.search(query().page(paging[0], paging[1]).build()),
                    ScheduleErrorCode.INVALID_WORK_INPUT);
        }
        assertThat(service.search(query().keyword(" " + "가".repeat(100) + " ")
                                .page(100, 100)
                                .build())
                        .size())
                .isEqualTo(100);
        assertThat(fixture.workRepository.searches())
                .singleElement()
                .satisfies(criteria -> assertThat(criteria.keyword()).hasSize(100));
    }

    @Test
    @DisplayName("계정 → 조직 식별자 → 구성원 → 요청자 종류(기사 불가) → 조건 순서로 확인한다")
    void checksOrder() {
        QueryBuilder invalid = query().technicianId(0L).page(-1, 0);

        assertThatThrownBy(() ->
                        service.search(invalid.account(null).organization(0L).build()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("accountId must not be null");
        fixture.assertRejected(
                () -> service.search(
                        invalid.account(ACCOUNT_ID).organization(0L).build()),
                ScheduleErrorCode.INVALID_WORK_INPUT);
        invalid.organization(ORGANIZATION_ID.value());
        fixture.assertRejected(() -> service.search(invalid.build()), ScheduleErrorCode.NOT_ORGANIZATION_MEMBER);
        fixture.givenTechnician(TECHNICIAN_ID);
        fixture.assertRejected(() -> service.search(invalid.build()), ScheduleErrorCode.ACTION_NOT_ALLOWED);
        assertThat(fixture.workRepository.searches()).isEmpty();
    }

    @Test
    @DisplayName("표시 정보 포트에는 그 쪽 작업의 기사·등록자·작업유형만 포트마다 한 번에 묻는다")
    void looksUpDisplayInfoOfPageOnly() {
        fixture.givenManager();
        WorkId shown = fixture.givenWork(WorkStatus.ACCEPTED);
        Work other = Work.register(
                ORGANIZATION_ID, "다른 쪽 작업", new MembershipId(77L), REGISTERED_AT, new WorkTypeId(9L), null, null);
        fixture.workRepository.store(other);
        fixture.workRepository.givenSearchResult(List.of(shown), List.of(NOW), 2);

        service.search(query().build());

        assertThat(fixture.technicianProfiles.lookups()).containsExactly(Set.of(TECHNICIAN_ID));
        assertThat(fixture.staffProfiles.lookups()).containsExactly(Set.of(REGISTRAR_ID));
        assertThat(fixture.workTypeLabels.lookups()).containsExactly(Set.of(WORK_TYPE_ID));
    }

    private static QueryBuilder query() {
        return new QueryBuilder();
    }

    /** 검색 요청을 기본값(조건 없음)에서 필요한 것만 바꿔 만든다. */
    private static final class QueryBuilder {

        private Long accountId = ACCOUNT_ID;
        private Long organizationId = ORGANIZATION_ID.value();
        private String keyword;
        private Set<WorkStatus> statuses;
        private Long technicianId;
        private Long workTypeId;
        private Long registrarId;
        private Instant startFrom;
        private Instant startTo;
        private boolean returnedByRejectionOnly;
        private boolean delayedOnly;
        private WorkSearchFilter.Sort sort;
        private Integer page;
        private Integer size;

        QueryBuilder account(Long value) {
            accountId = value;
            return this;
        }

        QueryBuilder organization(Long value) {
            organizationId = value;
            return this;
        }

        QueryBuilder keyword(String value) {
            keyword = value;
            return this;
        }

        QueryBuilder statuses(Set<WorkStatus> value) {
            statuses = value;
            return this;
        }

        QueryBuilder technicianId(Long value) {
            technicianId = value;
            return this;
        }

        QueryBuilder workTypeId(Long value) {
            workTypeId = value;
            return this;
        }

        QueryBuilder registrarId(Long value) {
            registrarId = value;
            return this;
        }

        QueryBuilder start(Instant from, Instant to) {
            startFrom = from;
            startTo = to;
            return this;
        }

        QueryBuilder returnedByRejectionOnly() {
            returnedByRejectionOnly = true;
            return this;
        }

        QueryBuilder delayedOnly() {
            delayedOnly = true;
            return this;
        }

        QueryBuilder sort(WorkSearchFilter.Sort value) {
            sort = value;
            return this;
        }

        QueryBuilder page(Integer pageNumber, Integer pageSize) {
            page = pageNumber;
            size = pageSize;
            return this;
        }

        SearchWorksQuery build() {
            return new SearchWorksQuery(
                    accountId,
                    organizationId,
                    new WorkSearchFilter(
                            keyword,
                            statuses,
                            technicianId,
                            workTypeId,
                            registrarId,
                            startFrom,
                            startTo,
                            returnedByRejectionOnly,
                            delayedOnly,
                            sort),
                    page,
                    size);
        }
    }
}
