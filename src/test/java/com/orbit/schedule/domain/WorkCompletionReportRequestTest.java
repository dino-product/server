package com.orbit.schedule.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("작업의 완료보고 요청")
class WorkCompletionReportRequestTest {

    private static final OrganizationId ORGANIZATION = new OrganizationId(100L);
    private static final MembershipId REGISTRAR = new MembershipId(1L);
    private static final MembershipId MANAGER = new MembershipId(2L);
    private static final MembershipId STAFF = new MembershipId(3L);
    private static final TechnicianId TECHNICIAN = new TechnicianId(7L);
    private static final Instant REGISTERED_AT = Instant.parse("2026-10-01T01:00:00Z");
    private static final Instant ASSIGNED_AT = REGISTERED_AT.plus(Duration.ofMinutes(5));
    private static final Instant ACCEPTED_AT = REGISTERED_AT.plus(Duration.ofMinutes(10));
    private static final Instant STARTED_AT = REGISTERED_AT.plus(Duration.ofMinutes(20));
    private static final Instant START = Instant.parse("2026-10-01T02:00:00Z");
    /** 예정 종료 시각(시작 + 2시간). */
    private static final Instant END = START.plus(Duration.ofHours(2));

    @Test
    @DisplayName("작업중 작업은 예정 종료 시각 정각부터 요청할 수 있고, 요청 시각(마이크로초)과 요청한 관리자를 남긴다")
    void recordsRequestFromScheduledEnd() {
        Work work = inProgress();

        CompletionReportRequest request = work.requestCompletionReport(END.plusNanos(1_234_567), MANAGER);

        CompletionReportRequest expected = new CompletionReportRequest(END.plusNanos(1_234_000), MANAGER);
        assertThat(request).isEqualTo(expected);
        assertThat(work.completionReportRequests()).containsExactly(expected);
        assertThat(work.status()).isEqualTo(WorkStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("예정 종료 시각 전에는 요청할 수 없다(상태 오류)")
    void rejectsBeforeScheduledEnd() {
        Work work = inProgress();

        assertThatThrownBy(() -> work.requestCompletionReport(END.minusNanos(1_000), MANAGER))
                .isInstanceOf(IllegalStateException.class);
        assertThat(work.completionReportRequests()).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("worksNotInProgress")
    @DisplayName("작업중이 아닌 작업은 예정 종료 시각이 지났어도 요청할 수 없다(완료보고가 있는 완료 작업 포함)")
    void rejectsWorkNotInProgress(String name, Consumer<Work> arrange) {
        Work work = registered();
        arrange.accept(work);

        assertThatThrownBy(() -> work.requestCompletionReport(END.plus(Duration.ofHours(1)), MANAGER))
                .isInstanceOf(IllegalStateException.class);
        assertThat(work.completionReportRequests()).isEmpty();
    }

    static Stream<Arguments> worksNotInProgress() {
        return Stream.of(
                Arguments.of("대기함(등록)", (Consumer<Work>) work -> {}),
                Arguments.of("배정됨", (Consumer<Work>) WorkCompletionReportRequestTest::assign),
                Arguments.of("작업전(수락)", (Consumer<Work>) work -> {
                    assign(work);
                    work.accept(ACCEPTED_AT);
                }),
                Arguments.of("완료", (Consumer<Work>) work -> {
                    start(work);
                    work.submitCompletionReport(new CompletionReport(null, null, null, "끝", null, null), END);
                }),
                Arguments.of("취소", (Consumer<Work>) work -> {
                    start(work);
                    work.cancel(END, MANAGER, CancellationReason.CUSTOMER_REQUEST, null);
                }));
    }

    @Test
    @DisplayName("같은 작업은 마지막 요청 뒤 30분이 지나야 다시 요청할 수 있다(29분 59초는 거부, 30분 정각은 허용). 요청자는 달라도 된다")
    void allowsAgainOnlyAfterInterval() {
        Work work = inProgress();
        work.requestCompletionReport(END, MANAGER);

        assertThatThrownBy(() -> work.requestCompletionReport(
                        END.plus(Duration.ofMinutes(29).plusSeconds(59)), STAFF))
                .isInstanceOf(CompletionReportRequestTooSoonException.class);
        assertThat(work.completionReportRequests()).hasSize(1);

        work.requestCompletionReport(END.plus(Work.COMPLETION_REPORT_REQUEST_INTERVAL), STAFF);
        assertThat(work.completionReportRequests())
                .containsExactly(
                        new CompletionReportRequest(END, MANAGER),
                        new CompletionReportRequest(END.plus(Duration.ofMinutes(30)), STAFF));
    }

    @Test
    @DisplayName("요청 시각이 시작 시각이나 마지막 요청보다 앞서면 입력 오류이고, 요청 간격보다 먼저 확인한다")
    void rejectsRequestBeforeEarlierRecords() {
        Instant lateStart = END.plus(Duration.ofHours(1));
        Work lateStarted = registered();
        assign(lateStarted);
        lateStarted.accept(ACCEPTED_AT);
        lateStarted.start(lateStart);
        assertThatThrownBy(() -> lateStarted.requestCompletionReport(lateStart.minusSeconds(1), MANAGER))
                .isInstanceOf(IllegalArgumentException.class);

        Work requested = inProgress();
        requested.requestCompletionReport(END.plus(Duration.ofHours(1)), MANAGER);
        assertThatThrownBy(() -> requested.requestCompletionReport(END.plus(Duration.ofMinutes(50)), MANAGER))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(requested.completionReportRequests()).hasSize(1);
    }

    @Test
    @DisplayName("요청 시각·요청자가 없으면 상태보다 먼저 입력 오류다")
    void rejectsMissingInputBeforeState() {
        Work work = registered();

        assertThatThrownBy(() -> work.requestCompletionReport(null, MANAGER))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> work.requestCompletionReport(END, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("요청할 수 있는지는 작업중·예정 종료 시각 지남·마지막 요청 뒤 30분 지남으로 판단한다")
    void tellsWhetherRequestable() {
        Work work = inProgress();

        assertThat(work.canRequestCompletionReportAt(END.minusNanos(1_000))).isFalse();
        assertThat(work.canRequestCompletionReportAt(END)).isTrue();

        work.requestCompletionReport(END, MANAGER);
        assertThat(work.canRequestCompletionReportAt(
                        END.plus(Duration.ofMinutes(29).plusSeconds(59))))
                .isFalse();
        assertThat(work.canRequestCompletionReportAt(END.plus(Duration.ofMinutes(30))))
                .isTrue();

        work.submitCompletionReport(new CompletionReport(null, null, null, "끝", null, null), END.plusSeconds(60));
        assertThat(work.canRequestCompletionReportAt(END.plus(Duration.ofHours(1))))
                .isFalse();
        assertThat(registered().canRequestCompletionReportAt(END)).isFalse();
    }

    @Test
    @DisplayName("요청 기록은 작업이 끝난 뒤에도 남고, 저장값을 그대로 복원하며 순서가 어긋나거나 등록보다 앞선 기록은 거부한다")
    void restoresRequests() {
        Work work = inProgress();
        work.requestCompletionReport(END, MANAGER);
        work.submitCompletionReport(new CompletionReport(null, null, null, "끝", null, null), END.plusSeconds(60));
        List<CompletionReportRequest> requests = List.of(
                new CompletionReportRequest(END, MANAGER),
                new CompletionReportRequest(END.plus(Duration.ofMinutes(30)), STAFF));

        assertThat(work.completionReportRequests()).containsExactly(new CompletionReportRequest(END, MANAGER));
        assertThat(restore(work, requests).completionReportRequests()).containsExactlyElementsOf(requests);
        assertThat(restore(work, null).completionReportRequests()).isEmpty();
        assertThatThrownBy(() -> restore(work, requests.reversed())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                        restore(work, List.of(new CompletionReportRequest(REGISTERED_AT.minusSeconds(1), MANAGER))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Work registered() {
        return Work.register(ORGANIZATION, "보일러 점검", REGISTRAR, REGISTERED_AT, null, null, null);
    }

    private static void assign(Work work) {
        work.assign(new WorkSchedule(TECHNICIAN, START, Duration.ofHours(2)), ASSIGNED_AT, MANAGER);
    }

    private static void start(Work work) {
        assign(work);
        work.accept(ACCEPTED_AT);
        work.start(STARTED_AT);
    }

    private static Work inProgress() {
        Work work = registered();
        start(work);
        return work;
    }

    private static Work restore(Work source, List<CompletionReportRequest> requests) {
        return Work.reconstitute(
                new WorkId(10L),
                source.organizationId(),
                source.name(),
                source.registrarId(),
                source.registeredAt(),
                source.workType().orElse(null),
                source.schedule().orElse(null),
                source.plannedSchedule().orElse(null),
                source.customerInfo(),
                source.paymentInfo(),
                source.status(),
                source.assignmentHistory(),
                source.completionReport().orElse(null),
                source.startedAt().orElse(null),
                source.completedAt().orElse(null),
                source.cancellation().orElse(null),
                source.statusCorrections(),
                source.sitePhotos(),
                requests,
                3L);
    }
}
