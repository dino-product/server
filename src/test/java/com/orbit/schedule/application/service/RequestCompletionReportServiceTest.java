package com.orbit.schedule.application.service;

import static com.orbit.schedule.application.service.ScheduleServiceFixture.ACCOUNT_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.MANAGER_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.NOW;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.ORGANIZATION_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.OTHER_ORGANIZATION_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.SETUP_MANAGER_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TECHNICIAN_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TWO_HOURS;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.UNKNOWN_WORK_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.orbit.schedule.CompletionReportRequested;
import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.application.port.in.command.dto.CompletionReportRequestInfo;
import com.orbit.schedule.application.port.in.command.dto.RequestCompletionReportCommand;
import com.orbit.schedule.domain.ActorRole;
import com.orbit.schedule.domain.CompletionReport;
import com.orbit.schedule.domain.CompletionReportRequest;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkId;
import com.orbit.schedule.domain.WorkStatus;

@DisplayName("완료보고 요청")
class RequestCompletionReportServiceTest {

    /** 예정 종료가 지금보다 1시간 앞서도록 시작한 일정. */
    private static final Instant OVERDUE_START = NOW.minus(Duration.ofHours(3));

    private final ScheduleServiceFixture fixture = new ScheduleServiceFixture();
    private final RequestCompletionReportService service = new RequestCompletionReportService(
            fixture.actorPort, fixture.workRepository, fixture.events, fixture.clock);

    @ParameterizedTest
    @EnumSource(ActorRole.class)
    @DisplayName("등록자가 아닌 직원·총관리자도 요청할 수 있고, 요청 시각·요청자를 남겨 저장한 뒤 담당 기사에게 알릴 요청을 발행한다")
    void recordsRequestAndPublishes(ActorRole role) {
        fixture.givenManager(role);
        WorkId id = overdueWork(WorkStatus.IN_PROGRESS);

        CompletionReportRequestInfo info = service.request(command(id.value()));

        assertThat(info).isEqualTo(new CompletionReportRequestInfo(NOW));
        Work saved = fixture.singleSaved();
        assertThat(saved.completionReportRequests()).containsExactly(new CompletionReportRequest(NOW, MANAGER_ID));
        assertThat(saved.status()).isEqualTo(WorkStatus.IN_PROGRESS);
        assertThat(fixture.events.published())
                .containsExactly(new CompletionReportRequested(
                        id.value(), ORGANIZATION_ID.value(), TECHNICIAN_ID.value(), "늦은 작업", NOW));
    }

    @Test
    @DisplayName("예정 종료 시각 정각이면 요청할 수 있고, 그 전이면 409 SCHEDULE-002이며 저장·발행하지 않는다")
    void requiresScheduledEndPassed() {
        fixture.givenManager();
        WorkId endsNow = fixture.givenWork(
                ORGANIZATION_ID, "정각 종료", WorkStatus.IN_PROGRESS, TECHNICIAN_ID, NOW.minus(TWO_HOURS));
        WorkId endsLater = fixture.givenWork(
                ORGANIZATION_ID,
                "진행 중",
                WorkStatus.IN_PROGRESS,
                TECHNICIAN_ID,
                NOW.minus(TWO_HOURS).plusSeconds(1));

        fixture.assertRejected(() -> service.request(command(endsLater.value())), ScheduleErrorCode.INVALID_WORK_STATE);

        service.request(command(endsNow.value()));
        assertThat(fixture.singleSaved().id()).contains(endsNow);
    }

    @ParameterizedTest
    @EnumSource(
            value = WorkStatus.class,
            names = {"REGISTERED", "PENDING_ACCEPTANCE", "ACCEPTED", "COMPLETED", "CANCELLED"})
    @DisplayName("작업중이 아닌 작업은 예정 종료가 지났어도 409 SCHEDULE-002다")
    void rejectsWorkNotInProgress(WorkStatus status) {
        fixture.givenManager();
        WorkId id = overdueWork(status);

        fixture.assertRejected(() -> service.request(command(id.value())), ScheduleErrorCode.INVALID_WORK_STATE);
    }

    @Test
    @DisplayName("마지막 요청 뒤 30분이 안 됐으면 429 SCHEDULE-019이고 저장·발행하지 않는다")
    void rejectsRequestWithinInterval() {
        fixture.givenManager();
        WorkId id = overdueWorkRequestedAt(NOW.minus(Duration.ofMinutes(29).plusSeconds(59)));

        fixture.assertRejected(
                () -> service.request(command(id.value())), ScheduleErrorCode.COMPLETION_REPORT_REQUESTED_RECENTLY);
    }

    @Test
    @DisplayName("마지막 요청 뒤 30분이 지났으면 다른 관리자가 다시 요청할 수 있고 요청 기록이 쌓인다")
    void allowsRequestAfterInterval() {
        fixture.givenManager();
        Instant previous = NOW.minus(Duration.ofMinutes(30));
        WorkId id = overdueWorkRequestedAt(previous);

        service.request(command(id.value()));

        assertThat(fixture.singleSaved().completionReportRequests())
                .containsExactly(
                        new CompletionReportRequest(previous, SETUP_MANAGER_ID),
                        new CompletionReportRequest(NOW, MANAGER_ID));
        assertThat(fixture.events.published()).hasSize(1);
    }

    @Test
    @DisplayName("요청한 뒤 완료된 작업은 요청 간격보다 상태 오류가 먼저다")
    void checksStateBeforeInterval() {
        fixture.givenManager();
        Work work = fixture.stored(overdueWorkRequestedAt(NOW.minus(Duration.ofMinutes(1))));
        work.submitCompletionReport(new CompletionReport(null, null, null, "끝", null, null), NOW);
        WorkId id = fixture.workRepository.store(work);

        fixture.assertRejected(() -> service.request(command(id.value())), ScheduleErrorCode.INVALID_WORK_STATE);
    }

    @Test
    @DisplayName("기사는 작업이 없거나 상태가 맞지 않아도 403 SCHEDULE-005, 구성원이 아니면 403 SCHEDULE-004다")
    void rejectsTechnicianAndNonMemberFirst() {
        WorkId notInProgress = overdueWork(WorkStatus.ACCEPTED);

        fixture.givenTechnician(TECHNICIAN_ID);
        for (long workId : new long[] {notInProgress.value(), UNKNOWN_WORK_ID, 0L}) {
            fixture.assertRejected(() -> service.request(command(workId)), ScheduleErrorCode.ACTION_NOT_ALLOWED);
        }
        fixture.assertRejected(
                () -> service.request(
                        new RequestCompletionReportCommand(99L, ORGANIZATION_ID.value(), UNKNOWN_WORK_ID)),
                ScheduleErrorCode.NOT_ORGANIZATION_MEMBER);
        fixture.assertRejected(
                () -> service.request(new RequestCompletionReportCommand(ACCOUNT_ID, 0L, UNKNOWN_WORK_ID)),
                ScheduleErrorCode.INVALID_WORK_INPUT);
    }

    @Test
    @DisplayName("작업 식별자 형식이 틀리면 400 SCHEDULE-003, 없거나 다른 조직의 작업이면 상태와 관계없이 404 SCHEDULE-001이다")
    void hidesMissingWorkBeforeState() {
        fixture.givenManager();
        WorkId otherOrganizationWork = fixture.givenWork(
                OTHER_ORGANIZATION_ID, "다른 조직 작업", WorkStatus.IN_PROGRESS, TECHNICIAN_ID, OVERDUE_START);

        fixture.assertRejected(() -> service.request(command(0L)), ScheduleErrorCode.INVALID_WORK_INPUT);
        for (long workId : new long[] {otherOrganizationWork.value(), UNKNOWN_WORK_ID}) {
            fixture.assertRejected(() -> service.request(command(workId)), ScheduleErrorCode.WORK_NOT_FOUND);
        }
    }

    @Test
    @DisplayName("계정 식별자가 없으면 인증 계층의 프로그래밍 오류다")
    void requiresAccountId() {
        assertThatThrownBy(() -> service.request(
                        new RequestCompletionReportCommand(null, ORGANIZATION_ID.value(), UNKNOWN_WORK_ID)))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("저장 자리에서 다른 요청·완료보고가 먼저 작업을 바꾼 것이 드러나면 409 SCHEDULE-016이고 발행하지 않는다")
    void rejectsConcurrentChangeWithoutPublishing() {
        fixture.givenManager();
        WorkId id = overdueWork(WorkStatus.IN_PROGRESS);
        fixture.workRepository.failNextSaveWithConcurrentChange();

        fixture.assertRejected(() -> service.request(command(id.value())), ScheduleErrorCode.WORK_CHANGED);
    }

    private WorkId overdueWork(WorkStatus status) {
        return fixture.givenWork(ORGANIZATION_ID, "늦은 작업", status, TECHNICIAN_ID, OVERDUE_START);
    }

    private WorkId overdueWorkRequestedAt(Instant requestedAt) {
        Work work = fixture.stored(overdueWork(WorkStatus.IN_PROGRESS));
        work.requestCompletionReport(requestedAt, SETUP_MANAGER_ID);
        return fixture.workRepository.store(work);
    }

    private static RequestCompletionReportCommand command(Long workId) {
        return new RequestCompletionReportCommand(ACCOUNT_ID, ORGANIZATION_ID.value(), workId);
    }
}
