package com.orbit.schedule.application.service;

import static com.orbit.schedule.application.service.ScheduleServiceFixture.ACCOUNT_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.NOW;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.ORGANIZATION_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.OTHER_TECHNICIAN_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.SETUP_MANAGER_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TECHNICIAN_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TEN;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TWO_HOURS;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.application.port.in.query.dto.CompletionReportInfo;
import com.orbit.schedule.application.port.in.query.dto.CompletionReportInfo.Photo;
import com.orbit.schedule.application.port.in.query.dto.GetCompletionReportQuery;
import com.orbit.schedule.application.port.in.query.dto.WorkCompletionReportInfo;
import com.orbit.schedule.domain.ActualPaymentMethod;
import com.orbit.schedule.domain.CompletionReport;
import com.orbit.schedule.domain.MembershipId;
import com.orbit.schedule.domain.Money;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkId;
import com.orbit.schedule.domain.WorkSchedule;
import com.orbit.schedule.domain.WorkStatus;

/** 공통 오류 순서는 {@link WorkQueryRulesTest}가 다룬다. */
@DisplayName("완료보고 조회")
class GetCompletionReportServiceTest {

    private static final CompletionReport REPORT = new CompletionReport(
            List.of("before-1", "before-2"),
            List.of("after-1"),
            "필터",
            "교체 완료",
            new Money(120_000L),
            ActualPaymentMethod.CASH);

    private final ScheduleServiceFixture fixture = new ScheduleServiceFixture();
    private final GetCompletionReportService service =
            new GetCompletionReportService(fixture.actorPort, fixture.workRepository, photoId -> "url/" + photoId);

    @Test
    @DisplayName("관리자는 보고 내용과 사진의 내려받을 주소를 순서대로 받는다")
    void managerReadsReport() {
        fixture.givenManager();
        WorkId id = givenCompletedWork(REPORT);

        WorkCompletionReportInfo info = service.get(query(id.value()));

        assertThat(info.workId()).isEqualTo(id.value());
        assertThat(info.technicianId()).isEqualTo(TECHNICIAN_ID.value());
        assertThat(info.completedAt()).isEqualTo(NOW);
        CompletionReportInfo report = info.report();
        assertThat(report.beforePhotos())
                .containsExactly(new Photo("before-1", "url/before-1"), new Photo("before-2", "url/before-2"));
        assertThat(report.afterPhotos()).containsExactly(new Photo("after-1", "url/after-1"));
        assertThat(report.usedParts()).isEqualTo("필터");
        assertThat(report.workNote()).isEqualTo("교체 완료");
        assertThat(report.actualFee()).isEqualTo(120_000L);
        assertThat(report.actualPaymentMethod()).isEqualTo(ActualPaymentMethod.CASH);
    }

    @Test
    @DisplayName("입력하지 않은 항목은 null, 사진이 없으면 빈 목록이다")
    void mapsEmptyReport() {
        fixture.givenManager();
        WorkId id = givenCompletedWork(new CompletionReport(null, null, null, null, null, null));

        CompletionReportInfo report = service.get(query(id.value())).report();

        assertThat(report).isEqualTo(new CompletionReportInfo(List.of(), List.of(), null, null, null, null));
    }

    @Test
    @DisplayName("지금 담당인 기사는 자기 보고를 보고, 완료 전이면 보고를 찾을 수 없음이다")
    void currentTechnicianReadsReport() {
        fixture.givenTechnician(TECHNICIAN_ID);
        WorkId completed = givenCompletedWork(REPORT);
        WorkId inProgress = fixture.givenWork(WorkStatus.IN_PROGRESS);

        assertThat(service.get(query(completed.value())).report().workNote()).isEqualTo("교체 완료");
        fixture.assertRejected(
                () -> service.get(query(inProgress.value())), ScheduleErrorCode.COMPLETION_REPORT_NOT_FOUND);
    }

    @Test
    @DisplayName("다른 기사로 바뀌었거나 해제돼 대기함으로 돌아간 작업의 이전 기사는 담당이 아니라 권한 오류다")
    void rejectsFormerTechnician() {
        fixture.givenTechnician(TECHNICIAN_ID);
        Work reassigned = fixture.stored(fixture.givenWork(WorkStatus.ACCEPTED));
        reassigned.reassign(new WorkSchedule(OTHER_TECHNICIAN_ID, TEN, TWO_HOURS), NOW, SETUP_MANAGER_ID);
        WorkId reassignedId = fixture.workRepository.store(reassigned);
        Work unassigned = fixture.stored(fixture.givenWork(WorkStatus.ACCEPTED));
        unassigned.unassign(NOW, SETUP_MANAGER_ID);
        WorkId unassignedId = fixture.workRepository.store(unassigned);

        fixture.assertRejected(
                () -> service.get(query(reassignedId.value())), ScheduleErrorCode.NOT_ASSIGNED_TECHNICIAN);
        fixture.assertRejected(
                () -> service.get(query(unassignedId.value())), ScheduleErrorCode.NOT_ASSIGNED_TECHNICIAN);
    }

    @ParameterizedTest
    @EnumSource(
            value = WorkStatus.class,
            names = {"COMPLETED"},
            mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("완료보고가 없는 작업은 보고를 찾을 수 없음이다")
    void rejectsWorkWithoutReport(WorkStatus status) {
        fixture.givenManager();
        WorkId id = fixture.givenWork(status);

        fixture.assertRejected(() -> service.get(query(id.value())), ScheduleErrorCode.COMPLETION_REPORT_NOT_FOUND);
    }

    @Test
    @DisplayName("강제 변경으로 작업중에 되돌린 작업은 치운 보고를 돌려주지 않는다")
    void rejectsReopenedWork() {
        fixture.givenManager();
        Work work = fixture.stored(givenCompletedWork(REPORT));
        work.correctStatus(WorkStatus.IN_PROGRESS, NOW, new MembershipId(13L), "사진 누락");
        WorkId id = fixture.workRepository.store(work);

        fixture.assertRejected(() -> service.get(query(id.value())), ScheduleErrorCode.COMPLETION_REPORT_NOT_FOUND);
    }

    /** 기사 {@link ScheduleServiceFixture#TECHNICIAN_ID}가 주어진 보고로 지금 완료한 작업. */
    private WorkId givenCompletedWork(CompletionReport report) {
        Work work = fixture.stored(fixture.givenWork(WorkStatus.IN_PROGRESS));
        work.submitCompletionReport(report, NOW);
        return fixture.workRepository.store(work);
    }

    private static GetCompletionReportQuery query(Long workId) {
        return new GetCompletionReportQuery(ACCOUNT_ID, ORGANIZATION_ID.value(), workId);
    }
}
