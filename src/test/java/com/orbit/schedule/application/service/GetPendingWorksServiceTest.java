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
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.application.port.in.query.dto.GetTechnicianWorksQuery;
import com.orbit.schedule.application.port.in.query.dto.MyScheduleInfo;
import com.orbit.schedule.domain.Rejection;
import com.orbit.schedule.domain.RejectionReason;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkId;
import com.orbit.schedule.domain.WorkSchedule;
import com.orbit.schedule.domain.WorkStatus;

@DisplayName("기사 응답 필요 작업 조회")
class GetPendingWorksServiceTest {

    private final ScheduleServiceFixture fixture = new ScheduleServiceFixture();
    private final GetPendingWorksService service =
            new GetPendingWorksService(fixture.actorPort, fixture.workRepository, fixture.clock);

    @Test
    @DisplayName("날짜와 관계없이 지금 본인에게 배정돼 응답을 기다리는 작업을 시작시각 순으로 모두 담는다")
    void listsAllPendingWorksRegardlessOfDate() {
        fixture.givenTechnician(TECHNICIAN_ID);
        WorkId farFuture = fixture.givenWork(
                ORGANIZATION_ID, "두 달 뒤 작업", WorkStatus.PENDING_ACCEPT, TECHNICIAN_ID, TEN.plus(Duration.ofDays(60)));
        WorkId soon = fixture.givenWork(ORGANIZATION_ID, "곧 할 작업", WorkStatus.PENDING_ACCEPT, TECHNICIAN_ID, TEN);
        fixture.givenWork(ORGANIZATION_ID, "수락한 작업", WorkStatus.ACCEPTED, TECHNICIAN_ID, TEN);
        fixture.givenWork(ORGANIZATION_ID, "다른 기사 작업", WorkStatus.PENDING_ACCEPT, OTHER_TECHNICIAN_ID, TEN);
        fixture.givenWork(OTHER_ORGANIZATION_ID, "다른 조직 작업", WorkStatus.PENDING_ACCEPT, TECHNICIAN_ID, TEN);
        Work rejected = fixture.stored(fixture.givenWork(WorkStatus.PENDING_ACCEPT));
        rejected.reject(new Rejection(RejectionReason.SCHEDULE_CONFLICT, null), NOW);
        fixture.workRepository.store(rejected);
        Work reassigned = fixture.stored(fixture.givenWork(WorkStatus.PENDING_ACCEPT));
        reassigned.reassign(new WorkSchedule(OTHER_TECHNICIAN_ID, TEN, TWO_HOURS), NOW, SETUP_MANAGER_ID);
        fixture.workRepository.store(reassigned);

        MyScheduleInfo pending = service.get(query());

        assertThat(pending.works())
                .extracting(
                        MyScheduleInfo.ScheduledWork::workId,
                        MyScheduleInfo.ScheduledWork::status,
                        MyScheduleInfo.ScheduledWork::assignmentNumber)
                .containsExactly(
                        tuple(soon.value(), WorkStatus.PENDING_ACCEPT, 1),
                        tuple(farFuture.value(), WorkStatus.PENDING_ACCEPT, 1));
    }

    @Test
    @DisplayName("관리자는 403 SCHEDULE-005다")
    void rejectsManager() {
        fixture.givenManager();

        fixture.assertRejected(() -> service.get(query()), ScheduleErrorCode.ACTION_NOT_ALLOWED);
    }

    private static GetTechnicianWorksQuery query() {
        return new GetTechnicianWorksQuery(ACCOUNT_ID, ORGANIZATION_ID.value());
    }
}
