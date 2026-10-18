package com.orbit.schedule.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("작업의 미리 입력한 일정")
class WorkPlannedScheduleTest {

    private static final Instant REGISTERED_AT = Instant.parse("2020-01-01T00:00:00Z");

    private static final OrganizationId ORGANIZATION = new OrganizationId(100L);
    private static final MembershipId REGISTRAR = new MembershipId(1L);
    private static final TechnicianId TECHNICIAN = new TechnicianId(3L);
    private static final Instant NOW = Instant.parse("2026-09-24T01:00:00Z");
    private static final Instant TEN = Instant.parse("2026-09-25T01:00:00Z");
    private static final Duration TWO_HOURS = Duration.ofHours(2);

    @Test
    @DisplayName("일부만 입력한 담당기사·시간을 대기함 작업에 남긴다")
    void keepsPlannedScheduleInBacklog() {
        PlannedSchedule planned = new PlannedSchedule(TECHNICIAN, null, null);

        Work work = register(planned);

        assertThat(work.status()).isEqualTo(WorkStatus.REGISTERED);
        assertThat(work.plannedSchedule()).contains(planned);
        assertThat(work.schedule()).isEmpty();
    }

    @Test
    @DisplayName("모두 채워 배정하면 미리 입력한 일정은 실제 일정으로 대체된다")
    void replacesPlannedScheduleOnAssign() {
        Work work = register(new PlannedSchedule(TECHNICIAN, null, null));

        work.assign(new WorkSchedule(TECHNICIAN, TEN, TWO_HOURS), NOW, REGISTRAR);

        assertThat(work.plannedSchedule()).isEmpty();
        assertThat(work.schedule()).contains(new WorkSchedule(TECHNICIAN, TEN, TWO_HOURS));
    }

    @Test
    @DisplayName("기본정보를 수정해도 미리 입력한 일정은 그대로 둔다")
    void keepsPlannedScheduleOnDetailsChange() {
        PlannedSchedule planned = new PlannedSchedule(TECHNICIAN, TEN, null);
        Work work = register(planned);

        work.changeDetails("보일러 점검", null, null, null, null);

        assertThat(work.plannedSchedule()).contains(planned);
    }

    @Test
    @DisplayName("대기함에서 취소하면 미리 입력한 일정도 지운다")
    void clearsPlannedScheduleOnCancel() {
        Work work = register(new PlannedSchedule(null, TEN, null));

        work.cancel(NOW, REGISTRAR, CancellationReason.DUPLICATE_REGISTRATION, null);

        assertThat(work.plannedSchedule()).isEmpty();
    }

    @Test
    @DisplayName("미리 입력한 일정은 대기함 작업에만 복원한다")
    void restoresPlannedScheduleOnlyForBacklog() {
        PlannedSchedule planned = new PlannedSchedule(null, TEN, TWO_HOURS);

        assertThat(restore(WorkStatus.REGISTERED, null, planned, List.of()).plannedSchedule())
                .contains(planned);

        WorkSchedule schedule = new WorkSchedule(TECHNICIAN, TEN, TWO_HOURS);
        AssignmentHistory pending = new AssignmentHistory(schedule, NOW, REGISTRAR);
        assertThatThrownBy(() -> restore(WorkStatus.PENDING_ACCEPTANCE, schedule, planned, List.of(pending)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Only a REGISTERED work can have a planned schedule");
    }

    @Test
    @DisplayName("배정됐다 돌아온 대기함 작업에는 미리 입력한 일정을 복원하지 않는다")
    void rejectsPlannedScheduleOfReturnedWork() {
        WorkSchedule schedule = new WorkSchedule(TECHNICIAN, TEN, TWO_HOURS);
        AssignmentHistory rejected = new AssignmentHistory(schedule, NOW, REGISTRAR);
        rejected.reject(new Rejection(RejectionReason.OTHER, "장비 없음"), NOW.plusSeconds(60));

        assertThatThrownBy(() ->
                        restore(WorkStatus.REGISTERED, null, new PlannedSchedule(null, TEN, null), List.of(rejected)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Only a never-assigned work can have a planned schedule");
    }

    private static Work register(PlannedSchedule planned) {
        return Work.register(ORGANIZATION, "에어컨 수리", REGISTRAR, REGISTERED_AT, null, null, null, planned);
    }

    private static Work restore(
            WorkStatus status, WorkSchedule schedule, PlannedSchedule planned, List<AssignmentHistory> history) {
        return Work.reconstitute(
                new WorkId(10L),
                ORGANIZATION,
                "에어컨 수리",
                REGISTRAR,
                REGISTERED_AT,
                null,
                schedule,
                planned,
                null,
                null,
                status,
                history,
                null,
                null,
                null,
                null,
                List.of());
    }
}
