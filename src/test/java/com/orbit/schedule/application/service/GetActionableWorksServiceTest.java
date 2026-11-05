package com.orbit.schedule.application.service;

import static com.orbit.schedule.application.service.ScheduleServiceFixture.ACCOUNT_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.NOW;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.ORGANIZATION_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.OTHER_TECHNICIAN_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TECHNICIAN_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.application.port.in.query.dto.GetTechnicianWorksQuery;
import com.orbit.schedule.application.port.in.query.dto.MyScheduleInfo;
import com.orbit.schedule.domain.WorkId;
import com.orbit.schedule.domain.WorkStatus;

@DisplayName("기사가 지금 수행할 수 있는 작업 조회")
class GetActionableWorksServiceTest {

    private final ScheduleServiceFixture fixture = new ScheduleServiceFixture();
    private final GetActionableWorksService service =
            new GetActionableWorksService(fixture.actorPort, fixture.workRepository, fixture.clock);

    @Test
    @DisplayName("시작 가능 시간 안의 작업전 작업과, 언제 시작했든 작업중인 작업을 시작시각 순으로 담는다")
    void listsStartableAndInProgressWorks() {
        fixture.givenTechnician(TECHNICIAN_ID);
        WorkId startable = fixture.givenWork(
                ORGANIZATION_ID, "곧 시작", WorkStatus.ACCEPTED, TECHNICIAN_ID, NOW.plus(Duration.ofMinutes(5)));
        WorkId atLeadEdge = fixture.givenWork(
                ORGANIZATION_ID, "딱 10분 전", WorkStatus.ACCEPTED, TECHNICIAN_ID, NOW.plus(Duration.ofMinutes(10)));
        fixture.givenWork(
                ORGANIZATION_ID, "아직 이름(11분 전)", WorkStatus.ACCEPTED, TECHNICIAN_ID, NOW.plus(Duration.ofMinutes(11)));
        fixture.givenWork(
                ORGANIZATION_ID, "예정 종료 지남", WorkStatus.ACCEPTED, TECHNICIAN_ID, NOW.minus(Duration.ofHours(3)));
        WorkId lateInProgress = fixture.givenWork(
                ORGANIZATION_ID, "어제 시작한 작업", WorkStatus.IN_PROGRESS, TECHNICIAN_ID, NOW.minus(Duration.ofDays(1)));
        fixture.givenWork(
                ORGANIZATION_ID, "응답 전", WorkStatus.PENDING_ACCEPT, TECHNICIAN_ID, NOW.plus(Duration.ofMinutes(5)));
        fixture.givenWork(
                ORGANIZATION_ID, "다른 기사", WorkStatus.IN_PROGRESS, OTHER_TECHNICIAN_ID, NOW.minus(Duration.ofHours(1)));
        fixture.givenWork(ORGANIZATION_ID, "완료", WorkStatus.COMPLETED, TECHNICIAN_ID, NOW.minus(Duration.ofHours(1)));

        MyScheduleInfo actionable = service.get(new GetTechnicianWorksQuery(ACCOUNT_ID, ORGANIZATION_ID.value()));

        assertThat(actionable.works())
                .extracting(MyScheduleInfo.ScheduledWork::workId, MyScheduleInfo.ScheduledWork::status)
                .containsExactly(
                        tuple(lateInProgress.value(), WorkStatus.IN_PROGRESS),
                        tuple(startable.value(), WorkStatus.ACCEPTED),
                        tuple(atLeadEdge.value(), WorkStatus.ACCEPTED));
    }

    @Test
    @DisplayName("관리자는 403 SCHEDULE-005다")
    void rejectsManager() {
        fixture.givenManager();

        fixture.assertRejected(
                () -> service.get(new GetTechnicianWorksQuery(ACCOUNT_ID, ORGANIZATION_ID.value())),
                ScheduleErrorCode.ACTION_NOT_ALLOWED);
    }
}
