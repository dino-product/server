package com.orbit.schedule.application.service;

import static com.orbit.schedule.application.service.ScheduleServiceFixture.ACCEPTED_AT;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.ACCOUNT_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.NOW;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.ORGANIZATION_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.OTHER_TECHNICIAN_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.SETUP_MANAGER_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TECHNICIAN_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TEN;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TWO_HOURS;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.application.port.in.command.dto.SubmitCompletionReportCommand;
import com.orbit.schedule.domain.ActualPaymentMethod;
import com.orbit.schedule.domain.CompletionReport;
import com.orbit.schedule.domain.Money;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkId;
import com.orbit.schedule.domain.WorkSchedule;
import com.orbit.schedule.domain.WorkStatus;

@DisplayName("완료보고 제출")
class SubmitCompletionReportServiceTest {

    private final ScheduleServiceFixture fixture = new ScheduleServiceFixture();
    private final SubmitCompletionReportService service =
            new SubmitCompletionReportService(fixture.actorPort, fixture.workRepository, fixture.clock);

    @Test
    @DisplayName("담당 기사가 작업중인 작업에 보고를 제출하면 보고를 붙여 완료하고 지금 시각을 완료 시각으로 남긴다")
    void submitsReport() {
        fixture.givenTechnician(TECHNICIAN_ID);
        WorkId id = fixture.givenWork(WorkStatus.IN_PROGRESS);

        service.submit(command(id.value(), 1, "필터 1개", 150_000L, ActualPaymentMethod.CREDIT_CARD));

        Work saved = fixture.singleSaved();
        assertThat(saved.status()).isEqualTo(WorkStatus.COMPLETED);
        assertThat(saved.completedAt()).contains(NOW);
        assertThat(saved.completionReport())
                .contains(new CompletionReport(
                        List.of("before.jpg"),
                        List.of("after.jpg"),
                        "필터 1개",
                        "교체 완료",
                        new Money(150_000L),
                        ActualPaymentMethod.CREDIT_CARD));
        assertThat(fixture.scheduleLock.locks()).isEmpty();
    }

    @Test
    @DisplayName("실제 금액·결제수단을 비워 보내면 계획값으로 채우지 않고 비운 채 남긴다")
    void keepsActualPaymentEmpty() {
        fixture.givenTechnician(TECHNICIAN_ID);
        WorkId id = fixture.givenWork(WorkStatus.IN_PROGRESS);

        service.submit(command(id.value(), 1, null, null, null));

        CompletionReport report = fixture.singleSaved().completionReport().orElseThrow();
        assertThat(report.actualFee()).isEmpty();
        assertThat(report.actualPaymentMethod()).isEmpty();
    }

    @Test
    @DisplayName("같은 보고로 이미 완료한 작업에 다시 보내면 아무것도 바꾸지 않고 성공하고, 다른 보고면 상태 오류다")
    void treatsRepeatedSameReportAsSuccess() {
        fixture.givenTechnician(TECHNICIAN_ID);
        Work work = fixture.stored(fixture.givenWork(WorkStatus.IN_PROGRESS));
        work.submitCompletionReport(
                new CompletionReport(List.of("before.jpg"), List.of("after.jpg"), "필터 1개", "교체 완료", null, null),
                ACCEPTED_AT);
        WorkId id = fixture.workRepository.store(work);

        service.submit(command(id.value(), 1, "필터 1개", null, null));

        assertThat(fixture.workRepository.saved()).isEmpty();
        fixture.assertRejected(
                () -> service.submit(command(id.value(), 1, "필터 2개", null, null)),
                ScheduleErrorCode.INVALID_WORK_STATE);
        assertThat(fixture.stored(id).completionReport().orElseThrow().usedParts())
                .contains("필터 1개");
    }

    @Test
    @DisplayName("작업중이 아니면 제출할 수 없다")
    void rejectsOutsideInProgress() {
        fixture.givenTechnician(TECHNICIAN_ID);
        WorkId accepted = fixture.givenWork(WorkStatus.ACCEPTED);
        Work cancelled = fixture.stored(fixture.givenWork(WorkStatus.IN_PROGRESS));
        cancelled.cancel(ACCEPTED_AT.plusSeconds(60), SETUP_MANAGER_ID);
        WorkId cancelledInProgress = fixture.workRepository.store(cancelled);

        for (WorkId id : new WorkId[] {accepted, cancelledInProgress}) {
            fixture.assertRejected(
                    () -> service.submit(command(id.value(), 1, null, null, null)),
                    ScheduleErrorCode.INVALID_WORK_STATE);
        }
    }

    @Test
    @DisplayName("사진이 6장을 넘거나 빈 항목이 있거나, 메모·부품이 255자를 넘거나, 금액이 음수면 입력 오류다")
    void rejectsInvalidReport() {
        fixture.givenTechnician(TECHNICIAN_ID);
        WorkId id = fixture.givenWork(WorkStatus.IN_PROGRESS);
        List<String> sevenPhotos = Collections.nCopies(7, "photo.jpg");

        for (SubmitCompletionReportCommand invalid : new SubmitCompletionReportCommand[] {
            report(id.value(), sevenPhotos, null, null, null, null),
            report(id.value(), null, sevenPhotos, null, null, null),
            report(id.value(), Arrays.asList("photo.jpg", null), null, null, null, null),
            report(id.value(), null, null, "가".repeat(256), null, null),
            report(id.value(), null, null, null, "가".repeat(256), null),
            report(id.value(), null, null, null, null, -1L)
        }) {
            fixture.assertRejected(() -> service.submit(invalid), ScheduleErrorCode.INVALID_WORK_INPUT);
        }
    }

    @Test
    @DisplayName("보고 입력이 잘못되면 담당 여부·최신 여부·상태보다 먼저 입력 오류다")
    void checksReportBeforeOwnershipAndState() {
        fixture.givenTechnician(TECHNICIAN_ID);
        Work reassigned = fixture.stored(fixture.givenWork(WorkStatus.ACCEPTED));
        reassigned.reassign(new WorkSchedule(OTHER_TECHNICIAN_ID, TEN, TWO_HOURS), NOW, SETUP_MANAGER_ID);
        WorkId reassignedId = fixture.workRepository.store(reassigned);
        WorkId accepted = fixture.givenWork(WorkStatus.ACCEPTED);

        fixture.assertRejected(
                () -> service.submit(command(reassignedId.value(), 2, null, -1L, null)),
                ScheduleErrorCode.INVALID_WORK_INPUT);
        fixture.assertRejected(
                () -> service.submit(command(reassignedId.value(), 1, null, -1L, null)),
                ScheduleErrorCode.INVALID_WORK_INPUT);
        fixture.assertRejected(
                () -> service.submit(command(accepted.value(), 1, null, -1L, null)),
                ScheduleErrorCode.INVALID_WORK_INPUT);
    }

    @Test
    @DisplayName("완료 시각이 시작 시각보다 앞서면(서버 간 시계 차이) 입력 오류다")
    void rejectsCompletionBeforeStart() {
        fixture.givenTechnician(TECHNICIAN_ID);
        Work work = fixture.stored(fixture.givenWork(WorkStatus.ACCEPTED));
        work.start(NOW.plusSeconds(60));
        WorkId id = fixture.workRepository.store(work);

        fixture.assertRejected(
                () -> service.submit(command(id.value(), 1, null, null, null)), ScheduleErrorCode.INVALID_WORK_INPUT);
    }

    private static SubmitCompletionReportCommand command(
            long workId, int number, String usedParts, Long actualFee, ActualPaymentMethod method) {
        return new SubmitCompletionReportCommand(
                ACCOUNT_ID,
                ORGANIZATION_ID.value(),
                workId,
                number,
                List.of("before.jpg"),
                List.of("after.jpg"),
                usedParts,
                "교체 완료",
                actualFee,
                method);
    }

    private static SubmitCompletionReportCommand report(
            long workId,
            List<String> beforePhotos,
            List<String> afterPhotos,
            String usedParts,
            String workNote,
            Long actualFee) {
        return new SubmitCompletionReportCommand(
                ACCOUNT_ID,
                ORGANIZATION_ID.value(),
                workId,
                1,
                beforePhotos,
                afterPhotos,
                usedParts,
                workNote,
                actualFee,
                null);
    }
}
