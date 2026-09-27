package com.orbit.schedule.application.service;

import static com.orbit.schedule.application.service.ScheduleServiceFixture.ACCEPTED_AT;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.ACCOUNT_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.NOW;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.ORGANIZATION_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.OTHER_ORGANIZATION_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TECHNICIAN_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TEN;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TWO_HOURS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.application.port.in.query.dto.SearchWorksQuery;
import com.orbit.schedule.application.port.in.query.dto.WorkSearchInfo;
import com.orbit.schedule.application.port.out.WorkSearchCriteria;
import com.orbit.schedule.domain.Rejection;
import com.orbit.schedule.domain.RejectionReason;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkId;
import com.orbit.schedule.domain.WorkStatus;

@DisplayName("작업 목록 검색")
class SearchWorksServiceTest {

    private final ScheduleServiceFixture fixture = new ScheduleServiceFixture();
    private final SearchWorksService service =
            new SearchWorksService(fixture.actorPort, fixture.workRepository, fixture.clock);

    @Test
    @DisplayName("비운 조건은 기본값(모든 상태, 최근 등록 먼저, 0쪽, 20건)으로 검색한다")
    void searchesWithDefaults() {
        fixture.givenManager();

        WorkSearchInfo info = service.search(query(null, null, null, null, null, false, false, null, null, null));

        assertThat(fixture.workRepository.searches())
                .containsExactly(new WorkSearchCriteria(
                        null, Set.of(), null, null, null, false, null, WorkSearchCriteria.Sort.REGISTERED_DESC, 0, 20));
        assertThat(info.page()).isZero();
        assertThat(info.size()).isEqualTo(20);
    }

    @Test
    @DisplayName("검색어는 앞뒤 공백을 빼고, 지연만 보기는 지금 시각으로 판정하도록 조건을 넘긴다")
    void passesNormalizedCriteria() {
        fixture.givenManager();
        Instant to = TEN.plus(Duration.ofDays(7));

        service.search(query(
                "  보일러  ",
                Set.of(WorkStatus.ACCEPTED, WorkStatus.IN_PROGRESS),
                TECHNICIAN_ID.value(),
                TEN,
                to,
                true,
                true,
                SearchWorksQuery.Sort.START_TIME_DESC,
                2,
                50));
        service.search(query("   ", null, null, null, null, false, false, SearchWorksQuery.Sort.START_TIME_ASC, 0, 1));

        assertThat(fixture.workRepository.searches())
                .containsExactly(
                        new WorkSearchCriteria(
                                "보일러",
                                Set.of(WorkStatus.ACCEPTED, WorkStatus.IN_PROGRESS),
                                TECHNICIAN_ID,
                                TEN,
                                to,
                                true,
                                NOW,
                                WorkSearchCriteria.Sort.START_TIME_DESC,
                                2,
                                50),
                        new WorkSearchCriteria(
                                null,
                                Set.of(),
                                null,
                                null,
                                null,
                                false,
                                null,
                                WorkSearchCriteria.Sort.START_TIME_ASC,
                                0,
                                1));
    }

    @Test
    @DisplayName("저장소가 돌려준 순서·전체 수 그대로 담고, 일정·고객 이름·지연·거절 반환을 표시한다")
    void mapsResult() {
        fixture.givenManager();
        Work rejected = fixture.stored(fixture.givenWork(WorkStatus.PENDING_ACCEPTANCE));
        rejected.reject(new Rejection(RejectionReason.SCOPE_MISMATCH, null), ACCEPTED_AT);
        WorkId rejectedId = fixture.workRepository.store(rejected);
        WorkId late = fixture.givenWork(
                ORGANIZATION_ID, "늦은 작업", WorkStatus.IN_PROGRESS, TECHNICIAN_ID, NOW.minus(Duration.ofHours(3)));
        fixture.givenWork(OTHER_ORGANIZATION_ID, "다른 조직 작업", WorkStatus.ACCEPTED, TECHNICIAN_ID, TEN);

        WorkSearchInfo info = service.search(query(null, null, null, null, null, false, false, null, null, null));

        assertThat(info.totalCount()).isEqualTo(2);
        assertThat(info.items())
                .containsExactly(
                        new WorkSearchInfo.Item(
                                late.value(),
                                "늦은 작업",
                                WorkStatus.IN_PROGRESS,
                                TECHNICIAN_ID.value(),
                                NOW.minus(Duration.ofHours(3)),
                                NOW.minus(Duration.ofHours(3)).plus(TWO_HOURS),
                                "홍길동",
                                true,
                                false),
                        new WorkSearchInfo.Item(
                                rejectedId.value(),
                                "대상 작업",
                                WorkStatus.REGISTERED,
                                null,
                                null,
                                null,
                                "홍길동",
                                false,
                                true));
    }

    @Test
    @DisplayName("검색어가 100자를 넘거나, 기사·쪽·크기·시작 구간·상태 형식이 틀리면 입력 오류다")
    void rejectsInvalidCriteria() {
        fixture.givenManager();
        Set<WorkStatus> withNull = new HashSet<>();
        withNull.add(null);

        fixture.assertRejected(
                () -> service.search(query("가".repeat(101), null, null, null, null, false, false, null, null, null)),
                ScheduleErrorCode.INVALID_WORK_INPUT);
        fixture.assertRejected(
                () -> service.search(query(null, null, 0L, null, null, false, false, null, null, null)),
                ScheduleErrorCode.INVALID_WORK_INPUT);
        fixture.assertRejected(
                () -> service.search(query(null, withNull, null, null, null, false, false, null, null, null)),
                ScheduleErrorCode.INVALID_WORK_INPUT);
        for (Instant[] period : new Instant[][] {{TEN, null}, {null, TEN}, {TEN, TEN}, {TEN.plusSeconds(1), TEN}}) {
            fixture.assertRejected(
                    () -> service.search(query(null, null, null, period[0], period[1], false, false, null, null, null)),
                    ScheduleErrorCode.INVALID_WORK_INPUT);
        }
        for (int[] paging : new int[][] {{-1, 20}, {0, 0}, {0, 101}}) {
            fixture.assertRejected(
                    () -> service.search(query(null, null, null, null, null, false, false, null, paging[0], paging[1])),
                    ScheduleErrorCode.INVALID_WORK_INPUT);
        }
        assertThat(service.search(query("가".repeat(100), null, null, null, null, false, false, null, 0, 100))
                        .size())
                .isEqualTo(100);
        assertThat(fixture.workRepository.searches()).hasSize(1);
    }

    @Test
    @DisplayName("계정 → 조직 식별자 → 구성원 → 요청자 종류(기사 불가) → 조건 순서로 확인한다")
    void checksOrder() {
        SearchWorksQuery invalid = query(null, null, 0L, null, null, false, false, null, -1, 0);

        assertThatThrownBy(() -> service.search(
                        new SearchWorksQuery(null, 0L, null, null, 0L, null, null, false, false, null, -1, 0)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("accountId must not be null");
        fixture.assertRejected(
                () -> service.search(
                        new SearchWorksQuery(ACCOUNT_ID, 0L, null, null, 0L, null, null, false, false, null, -1, 0)),
                ScheduleErrorCode.INVALID_WORK_INPUT);
        fixture.assertRejected(() -> service.search(invalid), ScheduleErrorCode.NOT_ORGANIZATION_MEMBER);
        fixture.givenTechnician(TECHNICIAN_ID);
        fixture.assertRejected(() -> service.search(invalid), ScheduleErrorCode.ACTION_NOT_ALLOWED);
        assertThat(fixture.workRepository.searches()).isEmpty();
    }

    private static SearchWorksQuery query(
            String keyword,
            Set<WorkStatus> statuses,
            Long technicianId,
            Instant startFrom,
            Instant startTo,
            boolean returnedByRejectionOnly,
            boolean delayedOnly,
            SearchWorksQuery.Sort sort,
            Integer page,
            Integer size) {
        return new SearchWorksQuery(
                ACCOUNT_ID,
                ORGANIZATION_ID.value(),
                keyword,
                statuses,
                technicianId,
                startFrom,
                startTo,
                returnedByRejectionOnly,
                delayedOnly,
                sort,
                page,
                size);
    }
}
