package com.orbit.schedule.application.service;

import static com.orbit.schedule.application.service.ScheduleServiceFixture.ACCOUNT_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.NOW;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.ORGANIZATION_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.OTHER_ORGANIZATION_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.OTHER_TECHNICIAN_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TECHNICIAN_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TEN;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TWO_HOURS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.application.port.in.query.dto.GetTeamScheduleQuery;
import com.orbit.schedule.application.port.in.query.dto.TeamScheduleInfo;
import com.orbit.schedule.domain.ActorRole;
import com.orbit.schedule.domain.TechnicianId;
import com.orbit.schedule.domain.WorkId;
import com.orbit.schedule.domain.WorkStatus;

@DisplayName("팀 일정 조회")
class GetTeamScheduleServiceTest {

    private static final Instant FROM = NOW.minus(Duration.ofHours(4));
    private static final Instant TO = TEN.plus(Duration.ofDays(1));
    private static final TechnicianId UNNAMED_TECHNICIAN_ID = new TechnicianId(5L);

    private final ScheduleServiceFixture fixture = new ScheduleServiceFixture();
    private final List<Set<TechnicianId>> nameLookups = new ArrayList<>();
    private final GetTeamScheduleService service =
            new GetTeamScheduleService(fixture.actorPort, fixture.workRepository, (organizationId, technicianIds) -> {
                assertThat(organizationId).isEqualTo(ORGANIZATION_ID);
                nameLookups.add(technicianIds);
                return Map.of(TECHNICIAN_ID, "김기사", OTHER_TECHNICIAN_ID, "이기사");
            });

    @Test
    @DisplayName("구간의 조직 작업을 기사·시작시각 순으로 담고, 다른 기사 작업은 시간·기사 이름·상태만, 본인 작업은 작업 식별자·작업명도 담는다")
    void listsTeamSlots() {
        fixture.givenTechnician(TECHNICIAN_ID);
        WorkId mine = fixture.givenWork(ORGANIZATION_ID, "내 작업", WorkStatus.ACCEPTED, TECHNICIAN_ID, TEN);
        fixture.givenWork(ORGANIZATION_ID, "남의 작업", WorkStatus.IN_PROGRESS, OTHER_TECHNICIAN_ID, TEN.plus(TWO_HOURS));
        fixture.givenWork(ORGANIZATION_ID, "남의 먼저 작업", WorkStatus.COMPLETED, OTHER_TECHNICIAN_ID, TEN);
        fixture.givenWork(ORGANIZATION_ID, "이름 없는 기사 작업", WorkStatus.PENDING_ACCEPTANCE, UNNAMED_TECHNICIAN_ID, TEN);
        fixture.givenWork(ORGANIZATION_ID, "취소 작업", WorkStatus.CANCELLED, OTHER_TECHNICIAN_ID, TEN);
        fixture.givenWork(OTHER_ORGANIZATION_ID, "다른 조직 작업", WorkStatus.ACCEPTED, OTHER_TECHNICIAN_ID, TEN);

        TeamScheduleInfo schedule = service.get(query(FROM, TO));

        assertThat(schedule.slots())
                .containsExactly(
                        new TeamScheduleInfo.Slot(
                                TECHNICIAN_ID.value(),
                                "김기사",
                                TEN,
                                TEN.plus(TWO_HOURS),
                                WorkStatus.ACCEPTED,
                                true,
                                mine.value(),
                                "내 작업"),
                        new TeamScheduleInfo.Slot(
                                OTHER_TECHNICIAN_ID.value(),
                                "이기사",
                                TEN,
                                TEN.plus(TWO_HOURS),
                                WorkStatus.COMPLETED,
                                false,
                                null,
                                null),
                        new TeamScheduleInfo.Slot(
                                OTHER_TECHNICIAN_ID.value(),
                                "이기사",
                                TEN.plus(TWO_HOURS),
                                TEN.plus(TWO_HOURS).plus(TWO_HOURS),
                                WorkStatus.IN_PROGRESS,
                                false,
                                null,
                                null),
                        new TeamScheduleInfo.Slot(
                                UNNAMED_TECHNICIAN_ID.value(),
                                null,
                                TEN,
                                TEN.plus(TWO_HOURS),
                                WorkStatus.PENDING_ACCEPTANCE,
                                false,
                                null,
                                null));
        assertThat(nameLookups).containsExactly(Set.of(TECHNICIAN_ID, OTHER_TECHNICIAN_ID, UNNAMED_TECHNICIAN_ID));
    }

    @Test
    @DisplayName("같은 기사·같은 시각의 칸은 상태 순이고, 저장 순서가 드러나지 않는다")
    void ordersSameTimeSlotsByVisibleValues() {
        fixture.givenTechnician(TECHNICIAN_ID);
        // 가짜 저장소는 저장 역순으로 돌려주므로, 서비스가 정렬하지 않으면 아래 기대 순서와 반대로 나온다.
        fixture.givenWork(ORGANIZATION_ID, "먼저 등록한 대기", WorkStatus.PENDING_ACCEPTANCE, OTHER_TECHNICIAN_ID, TEN);
        fixture.givenWork(ORGANIZATION_ID, "나중 등록한 완료", WorkStatus.COMPLETED, OTHER_TECHNICIAN_ID, TEN);
        WorkId mineFirst = fixture.givenWork(ORGANIZATION_ID, "내 첫 작업", WorkStatus.ACCEPTED, TECHNICIAN_ID, TEN);
        WorkId mineSecond = fixture.givenWork(ORGANIZATION_ID, "내 둘째 작업", WorkStatus.ACCEPTED, TECHNICIAN_ID, TEN);

        List<TeamScheduleInfo.Slot> slots = service.get(query(FROM, TO)).slots();

        assertThat(slots)
                .extracting(TeamScheduleInfo.Slot::workId)
                .containsExactly(mineFirst.value(), mineSecond.value(), null, null);
        assertThat(slots.subList(2, 4))
                .extracting(TeamScheduleInfo.Slot::status)
                .containsExactly(WorkStatus.PENDING_ACCEPTANCE, WorkStatus.COMPLETED);
    }

    @Test
    @DisplayName("구간에 작업이 없으면 기사 이름을 찾지 않고 빈 목록이다")
    void returnsEmptySchedule() {
        fixture.givenTechnician(TECHNICIAN_ID);

        assertThat(service.get(query(FROM, TO)).slots()).isEmpty();
        assertThat(nameLookups).isEmpty();
    }

    @Test
    @DisplayName("관리자는 요청할 수 없고, 계정 → 조직 식별자 → 구성원 → 요청자 종류 → 구간 순서로 확인한다")
    void checksOrder() {
        assertThatThrownBy(() -> service.get(new GetTeamScheduleQuery(null, 0L, null, null)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("accountId must not be null");
        fixture.assertRejected(
                () -> service.get(new GetTeamScheduleQuery(ACCOUNT_ID, 0L, null, null)),
                ScheduleErrorCode.INVALID_WORK_INPUT);
        fixture.assertRejected(() -> service.get(query(null, null)), ScheduleErrorCode.NOT_ORGANIZATION_MEMBER);
        fixture.givenManager(ActorRole.OWNER);
        fixture.assertRejected(() -> service.get(query(null, null)), ScheduleErrorCode.ACTION_NOT_ALLOWED);
        fixture.givenTechnician(TECHNICIAN_ID);
        fixture.assertRejected(() -> service.get(query(TO, FROM)), ScheduleErrorCode.INVALID_WORK_INPUT);
        assertThat(nameLookups).isEmpty();
    }

    private static GetTeamScheduleQuery query(Instant from, Instant to) {
        return new GetTeamScheduleQuery(ACCOUNT_ID, ORGANIZATION_ID.value(), from, to);
    }
}
