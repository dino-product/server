package com.orbit.schedule.application.service;

import static com.orbit.schedule.application.service.ScheduleServiceFixture.ACCOUNT_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.NOW;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.ORGANIZATION_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TECHNICIAN_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.application.port.in.query.dto.ExportWorksQuery;
import com.orbit.schedule.application.port.in.query.dto.WorkExportInfo;
import com.orbit.schedule.application.port.in.query.dto.WorkSearchFilter;
import com.orbit.schedule.application.port.out.WorkSearchCriteria;
import com.orbit.schedule.domain.PaymentMethod;
import com.orbit.schedule.domain.WorkId;
import com.orbit.schedule.domain.WorkStatus;

@DisplayName("작업내역 출력")
class ExportWorksServiceTest {

    private final ScheduleServiceFixture fixture = new ScheduleServiceFixture();
    private final ExportWorksService service = new ExportWorksService(
            fixture.actorPort,
            fixture.workRepository,
            fixture.technicianProfiles,
            fixture.staffProfiles,
            fixture.workTypeLabels,
            fixture.clock);

    @Test
    @DisplayName("검색과 같은 조건·정렬로 상한보다 한 건 더 한꺼번에 읽고 따로 세지 않는다")
    void listsWithSearchCriteriaAndLimitPlusOne() {
        fixture.givenManager();

        service.export(query(new WorkSearchFilter(
                null,
                Set.of(WorkStatus.REGISTERED),
                null,
                null,
                null,
                null,
                null,
                false,
                false,
                WorkSearchFilter.Sort.LAST_MODIFIED_DESC)));

        assertThat(fixture.workRepository.listings()).singleElement().satisfies(listing -> {
            assertThat(listing.limit()).isEqualTo(ExportWorksService.MAX_ROWS + 1);
            assertThat(listing.criteria().statuses()).containsExactly(WorkStatus.REGISTERED);
            assertThat(listing.criteria().sort()).isEqualTo(WorkSearchCriteria.Sort.LAST_MODIFIED_DESC);
        });
        assertThat(fixture.workRepository.searches()).isEmpty();
        assertThat(fixture.workRepository.counts()).isEmpty();
    }

    @Test
    @DisplayName("상태 조건을 비우면 검색처럼 취소 작업을 빼고, 취소를 고르면 담도록 조건을 넘긴다")
    void excludesCancelledUnlessChosen() {
        fixture.givenManager();

        service.export(query(null));
        service.export(query(new WorkSearchFilter(
                null, Set.of(WorkStatus.CANCELED), null, null, null, null, null, false, false, null)));

        assertThat(fixture.workRepository.listings())
                .extracting(listing -> listing.criteria().statuses())
                .containsExactly(
                        Set.of(
                                WorkStatus.REGISTERED,
                                WorkStatus.PENDING_ACCEPT,
                                WorkStatus.ACCEPTED,
                                WorkStatus.IN_PROGRESS,
                                WorkStatus.COMPLETED),
                        Set.of(WorkStatus.CANCELED));
    }

    @Test
    @DisplayName("읽은 작업을 목록 줄에 등록 때 정한 금액·결제 방식을 더한 출력 줄로 담고 출력 시각을 남긴다")
    void mapsRowsWithPlannedPayment() {
        fixture.givenManager();
        WorkId work = fixture.givenWork(WorkStatus.ACCEPTED);

        WorkExportInfo exported = service.export(query(null));

        assertThat(exported.exportedAt()).isEqualTo(NOW);
        assertThat(exported.rows())
                .extracting(row -> row.work().workId(), WorkExportInfo.Row::fee, WorkExportInfo.Row::paymentMethod)
                .containsExactly(tuple(work.value(), 150_000L, PaymentMethod.ON_SITE_CARD));
    }

    @Test
    @DisplayName("상한을 넘으면 거부하고, 상한만큼은 출력한다")
    void exportsUpToMaxRows() {
        fixture.givenManager();
        List<WorkId> works = IntStream.range(0, ExportWorksService.MAX_ROWS + 1)
                .mapToObj(i -> fixture.givenWork(WorkStatus.REGISTERED))
                .toList();

        fixture.assertRejected(() -> service.export(query(null)), ScheduleErrorCode.EXPORT_TOO_LARGE);
        fixture.workRepository.givenSearchResult(
                works.subList(0, ExportWorksService.MAX_ROWS),
                Collections.nCopies(ExportWorksService.MAX_ROWS, NOW),
                ExportWorksService.MAX_ROWS);
        assertThat(service.export(query(null)).rows()).hasSize(ExportWorksService.MAX_ROWS);
    }

    @Test
    @DisplayName("계정 → 조직 식별자 → 구성원 → 요청자 종류(기사 불가) → 조건 순서로 확인한다")
    void checksOrder() {
        WorkSearchFilter invalidFilter =
                new WorkSearchFilter(null, null, 0L, null, null, null, null, false, false, null);
        ExportWorksQuery invalid = new ExportWorksQuery(ACCOUNT_ID, ORGANIZATION_ID.value(), invalidFilter);

        assertThatThrownBy(() -> service.export(new ExportWorksQuery(null, 0L, invalidFilter)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("accountId must not be null");
        fixture.assertRejected(
                () -> service.export(new ExportWorksQuery(ACCOUNT_ID, 0L, invalidFilter)),
                ScheduleErrorCode.INVALID_WORK_INPUT);
        fixture.assertRejected(() -> service.export(invalid), ScheduleErrorCode.NOT_ORGANIZATION_MEMBER);
        fixture.givenTechnician(TECHNICIAN_ID);
        fixture.assertRejected(() -> service.export(invalid), ScheduleErrorCode.ACTION_NOT_ALLOWED);
        fixture.givenManager();
        fixture.assertRejected(() -> service.export(invalid), ScheduleErrorCode.INVALID_WORK_INPUT);
        assertThat(fixture.workRepository.listings()).isEmpty();
    }

    private static ExportWorksQuery query(WorkSearchFilter filter) {
        return new ExportWorksQuery(ACCOUNT_ID, ORGANIZATION_ID.value(), filter);
    }
}
