package com.orbit.schedule.application.service;

import static com.orbit.schedule.application.service.ScheduleServiceFixture.ACCEPTED_AT;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.ACCOUNT_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.NOW;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.ORGANIZATION_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.REGISTERED_AT;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.REGISTRAR_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TECHNICIAN_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.application.port.in.query.dto.BacklogSummaryInfo;
import com.orbit.schedule.application.port.in.query.dto.BacklogWorkInfo;
import com.orbit.schedule.application.port.in.query.dto.GetBacklogSummaryQuery;
import com.orbit.schedule.application.port.in.query.dto.StaffInfo;
import com.orbit.schedule.application.port.out.StaffProfile;
import com.orbit.schedule.application.port.out.WorkSearchCriteria;
import com.orbit.schedule.domain.Rejection;
import com.orbit.schedule.domain.RejectionReason;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkId;
import com.orbit.schedule.domain.WorkStatus;

@DisplayName("대기함 요약 조회")
class GetBacklogSummaryServiceTest {

    private final ScheduleServiceFixture fixture = new ScheduleServiceFixture();
    private final GetBacklogSummaryService service = new GetBacklogSummaryService(
            fixture.actorPort,
            fixture.workRepository,
            fixture.technicianProfiles,
            fixture.staffProfiles,
            fixture.workTypeLabels,
            fixture.clock);

    @Test
    @DisplayName("대기함 작업을 오래된 순으로 쪽을 나눠 읽고, 거절로 돌아온 작업 수는 따로 센다")
    void searchesBacklogPageAndCounts() {
        fixture.givenManager();

        service.get(query(2, 10));

        WorkSearchCriteria backlogPage = new WorkSearchCriteria(
                null,
                Set.of(WorkStatus.REGISTERED),
                null,
                null,
                null,
                null,
                null,
                false,
                null,
                WorkSearchCriteria.Sort.REGISTERED_ASC,
                2,
                10);
        assertThat(fixture.workRepository.searches()).containsExactly(backlogPage);
        assertThat(fixture.workRepository.counts()).singleElement().satisfies(criteria -> {
            assertThat(criteria.statuses()).containsExactly(WorkStatus.REGISTERED);
            assertThat(criteria.returnedByRejectionOnly()).isTrue();
        });
    }

    @Test
    @DisplayName("쪽의 작업을 등록자·거절 사유가 담긴 대기함 카드로 옮기고, 전체 수와 거절을 뺀 등록 수·거절 수를 담는다")
    void mapsCardsAndCounts() {
        fixture.givenManager();
        fixture.staffProfiles.given(ORGANIZATION_ID, new StaffProfile(REGISTRAR_ID, "김운영", false, null));
        WorkId fresh = fixture.givenWork(WorkStatus.REGISTERED);
        Work rejected = fixture.stored(fixture.givenWork(WorkStatus.PENDING_ACCEPT));
        rejected.reject(new Rejection(RejectionReason.SCHEDULE_CONFLICT, null), ACCEPTED_AT);
        WorkId rejectedId = fixture.workRepository.store(rejected);
        fixture.workRepository.givenSearchResult(List.of(fresh, rejectedId), List.of(NOW, NOW), 12);
        fixture.workRepository.givenCount(WorkSearchCriteria::returnedByRejectionOnly, 3);

        BacklogSummaryInfo summary = service.get(query(null, null));

        assertThat(summary.totalCount()).isEqualTo(12);
        assertThat(summary.registeredCount()).isEqualTo(9);
        assertThat(summary.rejectedCount()).isEqualTo(3);
        assertThat(summary.page()).isZero();
        assertThat(summary.size()).isEqualTo(GetBacklogSummaryService.DEFAULT_PAGE_SIZE);
        assertThat(summary.works())
                .extracting(
                        BacklogWorkInfo::workId,
                        BacklogWorkInfo::registrar,
                        BacklogWorkInfo::registeredAt,
                        BacklogWorkInfo::rejectionReason)
                .containsExactly(
                        tuple(
                                fresh.value(),
                                new StaffInfo(REGISTRAR_ID.value(), "김운영", false, null),
                                REGISTERED_AT,
                                null),
                        tuple(
                                rejectedId.value(),
                                new StaffInfo(REGISTRAR_ID.value(), "김운영", false, null),
                                REGISTERED_AT,
                                RejectionReason.SCHEDULE_CONFLICT));
    }

    @Test
    @DisplayName("쪽 번호·크기가 범위를 벗어나면 입력 오류다")
    void rejectsInvalidPaging() {
        fixture.givenManager();

        for (int[] paging : new int[][] {{-1, 5}, {0, 0}, {0, 101}, {101, 100}}) {
            fixture.assertRejected(
                    () -> service.get(query(paging[0], paging[1])), ScheduleErrorCode.INVALID_WORK_INPUT);
        }
        assertThat(fixture.workRepository.searches()).isEmpty();
        assertThat(fixture.workRepository.counts()).isEmpty();
    }

    @Test
    @DisplayName("계정 → 조직 식별자 → 구성원 → 요청자 종류(기사 불가) → 쪽 입력 순서로 확인한다")
    void checksOrder() {
        assertThatThrownBy(() -> service.get(new GetBacklogSummaryQuery(null, 0L, -1, 0)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("accountId must not be null");
        fixture.assertRejected(
                () -> service.get(new GetBacklogSummaryQuery(ACCOUNT_ID, 0L, -1, 0)),
                ScheduleErrorCode.INVALID_WORK_INPUT);
        fixture.assertRejected(() -> service.get(query(-1, 0)), ScheduleErrorCode.NOT_ORGANIZATION_MEMBER);
        fixture.givenTechnician(TECHNICIAN_ID);
        fixture.assertRejected(() -> service.get(query(-1, 0)), ScheduleErrorCode.ACTION_NOT_ALLOWED);
        assertThat(fixture.workRepository.searches()).isEmpty();
    }

    private static GetBacklogSummaryQuery query(Integer page, Integer size) {
        return new GetBacklogSummaryQuery(ACCOUNT_ID, ORGANIZATION_ID.value(), page, size);
    }
}
