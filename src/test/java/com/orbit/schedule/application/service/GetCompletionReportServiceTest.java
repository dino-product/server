package com.orbit.schedule.application.service;

import static com.orbit.schedule.application.service.ScheduleServiceFixture.ACCOUNT_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.NOW;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.ORGANIZATION_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.OTHER_ORGANIZATION_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.OTHER_TECHNICIAN_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.SETUP_MANAGER_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TECHNICIAN_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TEN;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TWO_HOURS;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.UNKNOWN_WORK_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.application.port.in.query.dto.GetCompletionReportQuery;
import com.orbit.schedule.application.port.in.query.dto.WorkCompletionReportInfo;
import com.orbit.schedule.application.port.in.query.dto.WorkCompletionReportInfo.PhotoView;
import com.orbit.schedule.domain.ActualPaymentMethod;
import com.orbit.schedule.domain.CompletionReport;
import com.orbit.schedule.domain.MembershipId;
import com.orbit.schedule.domain.Money;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkId;
import com.orbit.schedule.domain.WorkSchedule;
import com.orbit.schedule.domain.WorkStatus;

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
        WorkId id = givenCompletedWork();

        WorkCompletionReportInfo info = service.get(query(id.value()));

        assertThat(info.workId()).isEqualTo(id.value());
        assertThat(info.technicianId()).isEqualTo(TECHNICIAN_ID.value());
        assertThat(info.completedAt()).isEqualTo(NOW);
        assertThat(info.beforePhotos())
                .containsExactly(new PhotoView("before-1", "url/before-1"), new PhotoView("before-2", "url/before-2"));
        assertThat(info.afterPhotos()).containsExactly(new PhotoView("after-1", "url/after-1"));
        assertThat(info.usedParts()).isEqualTo("필터");
        assertThat(info.workNote()).isEqualTo("교체 완료");
        assertThat(info.actualFee()).isEqualTo(120_000L);
        assertThat(info.actualPaymentMethod()).isEqualTo(ActualPaymentMethod.CASH);
    }

    @Test
    @DisplayName("지금 담당인 기사는 자기 보고를 본다")
    void currentTechnicianReadsReport() {
        fixture.givenTechnician(TECHNICIAN_ID);
        WorkId id = givenCompletedWork();

        assertThat(service.get(query(id.value())).workNote()).isEqualTo("교체 완료");
    }

    @Test
    @DisplayName("배정받은 적 없는 기사에게는 작업을 숨기고, 이전 담당 기사는 담당이 아니라 권한 오류다")
    void checksTechnicianAssignment() {
        fixture.givenTechnician(OTHER_TECHNICIAN_ID);
        WorkId neverAssigned = givenCompletedWork();
        Work reassigned = fixture.stored(fixture.givenWork(WorkStatus.ACCEPTED));
        reassigned.reassign(new WorkSchedule(OTHER_TECHNICIAN_ID, TEN, TWO_HOURS), NOW, SETUP_MANAGER_ID);
        reassigned.reassign(new WorkSchedule(TECHNICIAN_ID, TEN, TWO_HOURS), NOW, SETUP_MANAGER_ID);
        WorkId formerlyAssigned = fixture.workRepository.store(reassigned);

        fixture.assertRejected(() -> service.get(query(neverAssigned.value())), ScheduleErrorCode.WORK_NOT_FOUND);
        fixture.assertRejected(
                () -> service.get(query(formerlyAssigned.value())), ScheduleErrorCode.NOT_ASSIGNED_TECHNICIAN);
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
        Work work = fixture.stored(givenCompletedWork());
        work.correctStatus(WorkStatus.IN_PROGRESS, NOW, new MembershipId(13L), "사진 누락");
        WorkId id = fixture.workRepository.store(work);

        fixture.assertRejected(() -> service.get(query(id.value())), ScheduleErrorCode.COMPLETION_REPORT_NOT_FOUND);
    }

    @Test
    @DisplayName("계정 식별자가 없으면 멈추고, 조직 식별자 → 구성원 → 작업 식별자 → 작업 조회 순서로 확인한다")
    void checksCommonOrder() {
        fixture.givenManager();
        WorkId otherOrganizationWork =
                fixture.givenWork(OTHER_ORGANIZATION_ID, "다른 조직 작업", WorkStatus.COMPLETED, TECHNICIAN_ID, TEN);

        assertThatThrownBy(() -> service.get(new GetCompletionReportQuery(null, ORGANIZATION_ID.value(), 1L)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("accountId must not be null");
        fixture.assertRejected(
                () -> service.get(new GetCompletionReportQuery(ACCOUNT_ID, 0L, 1L)),
                ScheduleErrorCode.INVALID_WORK_INPUT);
        fixture.assertRejected(
                () -> service.get(new GetCompletionReportQuery(ACCOUNT_ID, OTHER_ORGANIZATION_ID.value(), 1L)),
                ScheduleErrorCode.NOT_ORGANIZATION_MEMBER);
        fixture.assertRejected(() -> service.get(query(null)), ScheduleErrorCode.INVALID_WORK_INPUT);
        fixture.assertRejected(() -> service.get(query(UNKNOWN_WORK_ID)), ScheduleErrorCode.WORK_NOT_FOUND);
        fixture.assertRejected(
                () -> service.get(query(otherOrganizationWork.value())), ScheduleErrorCode.WORK_NOT_FOUND);
    }

    /** 기사 {@link ScheduleServiceFixture#TECHNICIAN_ID}가 {@link #REPORT}로 지금 완료한 작업. */
    private WorkId givenCompletedWork() {
        Work work = fixture.stored(fixture.givenWork(WorkStatus.IN_PROGRESS));
        work.submitCompletionReport(REPORT, NOW);
        return fixture.workRepository.store(work);
    }

    private static GetCompletionReportQuery query(Long workId) {
        return new GetCompletionReportQuery(ACCOUNT_ID, ORGANIZATION_ID.value(), workId);
    }
}
