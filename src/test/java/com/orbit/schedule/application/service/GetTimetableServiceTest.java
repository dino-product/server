package com.orbit.schedule.application.service;

import static com.orbit.schedule.application.service.ScheduleServiceFixture.ACCEPTED_AT;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.ACCOUNT_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.MANAGER_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.NOW;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.ORGANIZATION_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.OTHER_ORGANIZATION_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.OTHER_TECHNICIAN_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.REGISTERED_AT;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.REGISTRAR_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TECHNICIAN_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TEN;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TWO_HOURS;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.WORK_TYPE_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.application.port.in.query.dto.BacklogWorkInfo;
import com.orbit.schedule.application.port.in.query.dto.GetTimetableQuery;
import com.orbit.schedule.application.port.in.query.dto.LabelInfo;
import com.orbit.schedule.application.port.in.query.dto.StaffInfo;
import com.orbit.schedule.application.port.in.query.dto.TechnicianInfo;
import com.orbit.schedule.application.port.in.query.dto.TimetableInfo;
import com.orbit.schedule.application.port.out.StaffProfile;
import com.orbit.schedule.application.port.out.TechnicianProfile;
import com.orbit.schedule.application.port.out.TypeLabel;
import com.orbit.schedule.domain.ActorRole;
import com.orbit.schedule.domain.PlannedSchedule;
import com.orbit.schedule.domain.Rejection;
import com.orbit.schedule.domain.RejectionReason;
import com.orbit.schedule.domain.TechnicianId;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkId;
import com.orbit.schedule.domain.WorkSchedule;
import com.orbit.schedule.domain.WorkStatus;
import com.orbit.schedule.domain.WorkTypeId;

@DisplayName("타임테이블 조회")
class GetTimetableServiceTest {

    private static final Instant FROM = NOW.minus(Duration.ofHours(4));
    private static final Instant TO = TEN.plus(Duration.ofDays(1));
    private static final TypeLabel GENERAL = new TypeLabel(7L, "일반 기사", "#3366FF", false);
    private static final TypeLabel EXPERT = new TypeLabel(8L, "전문 기사", null, false);
    private static final TypeLabel INSTALL = new TypeLabel(WORK_TYPE_ID.value(), "설치", "#7C5CFF", false);
    private static final TypeLabel STAFF = new TypeLabel(5L, "접수", null, false);
    private static final TechnicianId UNNAMED_TECHNICIAN_ID = new TechnicianId(9L);
    private static final TechnicianInfo KIM = new TechnicianInfo(TECHNICIAN_ID.value(), "김민수", label(GENERAL));
    private static final TechnicianInfo LEE = new TechnicianInfo(OTHER_TECHNICIAN_ID.value(), "이철호", label(EXPERT));
    private static final StaffInfo REGISTRAR = new StaffInfo(REGISTRAR_ID.value(), "박서준", false, label(STAFF));

    private final ScheduleServiceFixture fixture = new ScheduleServiceFixture();
    private final GetTimetableService service = new GetTimetableService(
            fixture.actorPort,
            fixture.workRepository,
            fixture.technicianProfiles,
            fixture.staffProfiles,
            fixture.workTypeLabels,
            fixture.clock);

    @BeforeEach
    void givenDisplayInfos() {
        fixture.technicianProfiles.givenActive(
                ORGANIZATION_ID, new TechnicianProfile(OTHER_TECHNICIAN_ID, "이철호", EXPERT));
        fixture.technicianProfiles.givenActive(ORGANIZATION_ID, new TechnicianProfile(TECHNICIAN_ID, "김민수", GENERAL));
        fixture.technicianProfiles.givenActive(
                ORGANIZATION_ID, new TechnicianProfile(UNNAMED_TECHNICIAN_ID, null, null));
        fixture.staffProfiles.given(ORGANIZATION_ID, new StaffProfile(REGISTRAR_ID, "박서준", false, STAFF));
        fixture.workTypeLabels.given(ORGANIZATION_ID, INSTALL);
    }

    @Test
    @DisplayName("구간과 일정이 겹치는 취소 외 작업을 기사·시작시각 순으로 담고, 같은 기사의 겹친 활성 작업과 지연을 표시한다")
    void listsScheduledWorks() {
        fixture.givenManager();
        WorkId later = fixture.givenWork(
                ORGANIZATION_ID, "겹친 작업", WorkStatus.PENDING_ACCEPTANCE, TECHNICIAN_ID, TEN.plus(Duration.ofHours(1)));
        WorkId first = fixture.givenWork(ORGANIZATION_ID, "먼저 작업", WorkStatus.ACCEPTED, TECHNICIAN_ID, TEN);
        WorkId otherTechnician =
                fixture.givenWork(ORGANIZATION_ID, "다른 기사 작업", WorkStatus.COMPLETED, OTHER_TECHNICIAN_ID, TEN);
        WorkId late = fixture.givenWork(
                ORGANIZATION_ID, "늦은 작업", WorkStatus.IN_PROGRESS, OTHER_TECHNICIAN_ID, NOW.minus(Duration.ofHours(3)));
        fixture.givenWork(ORGANIZATION_ID, "취소 작업", WorkStatus.CANCELLED, TECHNICIAN_ID, TEN);
        fixture.givenWork(ORGANIZATION_ID, "구간 밖 작업", WorkStatus.ACCEPTED, TECHNICIAN_ID, TO);
        fixture.givenWork(OTHER_ORGANIZATION_ID, "다른 조직 작업", WorkStatus.ACCEPTED, TECHNICIAN_ID, TEN);

        TimetableInfo timetable = service.get(query(FROM, TO));

        assertThat(timetable.scheduledWorks())
                .extracting(
                        TimetableInfo.ScheduledWork::workId,
                        work -> work.technician().technicianId(),
                        TimetableInfo.ScheduledWork::status,
                        TimetableInfo.ScheduledWork::delayed,
                        TimetableInfo.ScheduledWork::conflicting)
                .containsExactly(
                        tuple(first.value(), TECHNICIAN_ID.value(), WorkStatus.ACCEPTED, false, true),
                        tuple(later.value(), TECHNICIAN_ID.value(), WorkStatus.PENDING_ACCEPTANCE, false, true),
                        tuple(late.value(), OTHER_TECHNICIAN_ID.value(), WorkStatus.IN_PROGRESS, true, false),
                        tuple(
                                otherTechnician.value(),
                                OTHER_TECHNICIAN_ID.value(),
                                WorkStatus.COMPLETED,
                                false,
                                false));
        assertThat(timetable.scheduledWorks().getFirst())
                .isEqualTo(new TimetableInfo.ScheduledWork(
                        first.value(),
                        "먼저 작업",
                        WorkStatus.ACCEPTED,
                        KIM,
                        TEN,
                        TEN.plus(TWO_HOURS),
                        false,
                        true,
                        label(INSTALL),
                        REGISTRAR,
                        0L));
    }

    @Test
    @DisplayName("활성 기사를 이름 순(이름 없는 기사는 맨 뒤)으로 행에 담는다")
    void listsActiveTechniciansAsRows() {
        fixture.givenManager();

        assertThat(service.get(query(FROM, TO)).technicians())
                .containsExactly(KIM, LEE, new TechnicianInfo(UNNAMED_TECHNICIAN_ID.value(), null, null));
    }

    @Test
    @DisplayName("표시 정보를 찾지 못한 기사·등록자·작업유형은 식별자만 담는다")
    void keepsIdentifiersWithoutDisplayInfo() {
        fixture.givenManager();
        Work work =
                Work.register(ORGANIZATION_ID, "모르는 유형 작업", MANAGER_ID, REGISTERED_AT, new WorkTypeId(77L), null, null);
        work.assign(new WorkSchedule(new TechnicianId(55L), TEN, TWO_HOURS), ACCEPTED_AT, MANAGER_ID);
        fixture.workRepository.store(work);

        TimetableInfo.ScheduledWork scheduled =
                service.get(query(FROM, TO)).scheduledWorks().getFirst();

        assertThat(scheduled.technician()).isEqualTo(new TechnicianInfo(55L, null, null));
        assertThat(scheduled.workType()).isEqualTo(new LabelInfo(77L, null, null));
        assertThat(scheduled.registrar()).isEqualTo(new StaffInfo(MANAGER_ID.value(), null, null, null));
    }

    @Test
    @DisplayName("기사유형 조건은 그 유형인 기사의 행과 작업 칸만 남기고, 겹침은 조건과 관계없이 판정한다")
    void filtersByTechnicianType() {
        fixture.givenManager();
        WorkId kims = fixture.givenWork(ORGANIZATION_ID, "김 작업", WorkStatus.ACCEPTED, TECHNICIAN_ID, TEN);
        fixture.givenWork(ORGANIZATION_ID, "이 작업", WorkStatus.ACCEPTED, OTHER_TECHNICIAN_ID, TEN);
        fixture.givenWork(
                ORGANIZATION_ID, "김 겹친 작업", WorkStatus.ACCEPTED, TECHNICIAN_ID, TEN.plus(Duration.ofHours(1)));

        TimetableInfo timetable = service.get(query(FROM, TO, Set.of(GENERAL.id()), null));

        assertThat(timetable.technicians()).containsExactly(KIM);
        assertThat(timetable.scheduledWorks())
                .extracting(work -> work.technician().technicianId(), TimetableInfo.ScheduledWork::conflicting)
                .containsOnly(tuple(TECHNICIAN_ID.value(), true));
        assertThat(timetable.scheduledWorks().getFirst().workId()).isEqualTo(kims.value());
    }

    @Test
    @DisplayName("작업유형 조건은 그 유형의 작업 칸만 남기고 기사 행과 대기함은 그대로 둔다")
    void filtersByWorkType() {
        fixture.givenManager();
        WorkId install = fixture.givenWork(ORGANIZATION_ID, "설치 작업", WorkStatus.ACCEPTED, TECHNICIAN_ID, TEN);
        Work untyped = Work.register(ORGANIZATION_ID, "유형 없는 작업", REGISTRAR_ID, REGISTERED_AT, null, null, null);
        untyped.assign(new WorkSchedule(OTHER_TECHNICIAN_ID, TEN, TWO_HOURS), ACCEPTED_AT, MANAGER_ID);
        fixture.workRepository.store(untyped);
        WorkId backlog = fixture.workRepository.store(
                Work.register(ORGANIZATION_ID, "유형 없는 대기 작업", REGISTRAR_ID, REGISTERED_AT, null, null, null));

        TimetableInfo timetable = service.get(query(FROM, TO, null, Set.of(WORK_TYPE_ID.value())));

        assertThat(timetable.scheduledWorks())
                .extracting(TimetableInfo.ScheduledWork::workId)
                .containsExactly(install.value());
        assertThat(timetable.technicians()).hasSize(3);
        assertThat(timetable.backlog()).extracting(BacklogWorkInfo::workId).containsExactly(backlog.value());
    }

    @Test
    @DisplayName("작업유형 조건으로 빠진 작업과 겹쳐도 남은 작업 칸에 겹침을 표시한다")
    void marksConflictWithFilteredOutWork() {
        fixture.givenManager();
        WorkId install = fixture.givenWork(ORGANIZATION_ID, "설치 작업", WorkStatus.ACCEPTED, TECHNICIAN_ID, TEN);
        Work untyped = Work.register(ORGANIZATION_ID, "유형 없는 작업", REGISTRAR_ID, REGISTERED_AT, null, null, null);
        untyped.assign(
                new WorkSchedule(TECHNICIAN_ID, TEN.plus(Duration.ofHours(1)), TWO_HOURS), ACCEPTED_AT, MANAGER_ID);
        fixture.workRepository.store(untyped);

        assertThat(service.get(query(FROM, TO, null, Set.of(WORK_TYPE_ID.value())))
                        .scheduledWorks())
                .extracting(TimetableInfo.ScheduledWork::workId, TimetableInfo.ScheduledWork::conflicting)
                .containsExactly(tuple(install.value(), true));
    }

    @Test
    @DisplayName("삭제된 작업유형은 시작 전 작업에는 유형 없음으로, 작업중·완료 작업에는 그대로 보이고 조건도 그 기준으로 거른다")
    void hidesDeletedWorkTypeBeforeStart() {
        fixture.givenManager();
        TypeLabel deleted = new TypeLabel(WORK_TYPE_ID.value(), "설치", "#7C5CFF", true);
        fixture.workTypeLabels.given(ORGANIZATION_ID, deleted);
        WorkId accepted = fixture.givenWork(ORGANIZATION_ID, "작업전", WorkStatus.ACCEPTED, TECHNICIAN_ID, TEN);
        WorkId inProgress = fixture.givenWork(ORGANIZATION_ID, "작업중", WorkStatus.IN_PROGRESS, OTHER_TECHNICIAN_ID, TEN);
        WorkId backlog = fixture.givenWork(WorkStatus.REGISTERED);

        TimetableInfo timetable = service.get(query(FROM, TO));

        assertThat(timetable.scheduledWorks())
                .extracting(TimetableInfo.ScheduledWork::workId, TimetableInfo.ScheduledWork::workType)
                .containsExactlyInAnyOrder(tuple(accepted.value(), null), tuple(inProgress.value(), label(deleted)));
        assertThat(timetable.backlog())
                .extracting(BacklogWorkInfo::workId, BacklogWorkInfo::workType)
                .containsExactly(tuple(backlog.value(), null));
        assertThat(service.get(query(FROM, TO, null, Set.of(WORK_TYPE_ID.value())))
                        .scheduledWorks())
                .extracting(TimetableInfo.ScheduledWork::workId)
                .containsExactly(inProgress.value());
    }

    @Test
    @DisplayName("완료된 작업과 겹쳐도 활성 작업끼리가 아니면 겹침으로 표시하지 않는다")
    void ignoresOverlapWithFinishedWork() {
        fixture.givenManager();
        WorkId completed = fixture.givenWork(ORGANIZATION_ID, "완료 작업", WorkStatus.COMPLETED, TECHNICIAN_ID, TEN);
        WorkId accepted = fixture.givenWork(ORGANIZATION_ID, "수락 작업", WorkStatus.ACCEPTED, TECHNICIAN_ID, TEN);

        assertThat(service.get(query(FROM, TO)).scheduledWorks())
                .extracting(TimetableInfo.ScheduledWork::workId, TimetableInfo.ScheduledWork::conflicting)
                .containsExactlyInAnyOrder(tuple(completed.value(), false), tuple(accepted.value(), false));
    }

    @Test
    @DisplayName("겹치는 짝이 구간 밖에 있어도 겹침으로 표시하고, 짝은 칸에 넣지 않는다")
    void marksConflictWithWorkOutsidePeriod() {
        fixture.givenManager();
        // 구간 전 작업(TEN ~ +2h)은 구간 시작(+2h) 전에 끝나고, 구간에 걸친 작업(+1h ~ +3h)과는 겹친다.
        fixture.givenWork(ORGANIZATION_ID, "구간 전 작업", WorkStatus.ACCEPTED, TECHNICIAN_ID, TEN);
        WorkId inside = fixture.givenWork(
                ORGANIZATION_ID, "구간에 걸친 작업", WorkStatus.ACCEPTED, TECHNICIAN_ID, TEN.plus(Duration.ofHours(1)));
        Instant from = TEN.plus(TWO_HOURS);

        TimetableInfo timetable = service.get(query(from, from.plus(Duration.ofDays(1))));

        assertThat(timetable.scheduledWorks())
                .extracting(TimetableInfo.ScheduledWork::workId, TimetableInfo.ScheduledWork::conflicting)
                .containsExactly(tuple(inside.value(), true));
    }

    @Test
    @DisplayName("같은 기사의 이어 붙은 작업은 겹침이 아니고, 시작시각이 같으면 작업 식별자 순이다")
    void ordersAndIgnoresAdjacentWorks() {
        fixture.givenManager();
        WorkId next =
                fixture.givenWork(ORGANIZATION_ID, "이어지는 작업", WorkStatus.ACCEPTED, TECHNICIAN_ID, TEN.plus(TWO_HOURS));
        WorkId first = fixture.givenWork(ORGANIZATION_ID, "앞 작업", WorkStatus.ACCEPTED, TECHNICIAN_ID, TEN);
        WorkId sameStartLater =
                fixture.givenWork(ORGANIZATION_ID, "같은 시각 작업", WorkStatus.COMPLETED, TECHNICIAN_ID, TEN);

        assertThat(service.get(query(FROM, TO)).scheduledWorks())
                .extracting(TimetableInfo.ScheduledWork::workId, TimetableInfo.ScheduledWork::conflicting)
                .containsExactly(
                        tuple(first.value(), false), tuple(sameStartLater.value(), false), tuple(next.value(), false));
    }

    @Test
    @DisplayName("대기함은 오래 등록된 순으로 담고, 거절로 돌아온 작업은 거절 사유를 담는다")
    void listsBacklogOldestFirst() {
        fixture.givenManager(ActorRole.OWNER);
        WorkId newer = fixture.workRepository.store(Work.register(
                ORGANIZATION_ID, "나중 등록", REGISTRAR_ID, REGISTERED_AT.plusSeconds(60), WORK_TYPE_ID, null, null));
        Work rejected = fixture.stored(fixture.givenWork(WorkStatus.PENDING_ACCEPTANCE));
        rejected.reject(new Rejection(RejectionReason.OTHER, "너무 멀어요"), ACCEPTED_AT);
        WorkId rejectedId = fixture.workRepository.store(rejected);
        fixture.givenWork(OTHER_ORGANIZATION_ID, "다른 조직 작업", WorkStatus.REGISTERED, TECHNICIAN_ID, TEN);

        assertThat(service.get(query(FROM, TO)).backlog())
                .containsExactly(
                        new BacklogWorkInfo(
                                rejectedId.value(),
                                "대상 작업",
                                label(INSTALL),
                                REGISTRAR,
                                REGISTERED_AT,
                                null,
                                null,
                                null,
                                true,
                                true,
                                true,
                                RejectionReason.OTHER,
                                "너무 멀어요",
                                false,
                                0L),
                        new BacklogWorkInfo(
                                newer.value(),
                                "나중 등록",
                                label(INSTALL),
                                REGISTRAR,
                                REGISTERED_AT.plusSeconds(60),
                                null,
                                null,
                                null,
                                true,
                                true,
                                false,
                                null,
                                null,
                                false,
                                0L));
    }

    @Test
    @DisplayName("등록 때 일부만 입력한 담당기사·시간과 부족한 항목을 대기함 카드에 담는다")
    void listsPlannedScheduleOfBacklog() {
        fixture.givenManager();
        WorkId technicianOnly = fixture.workRepository.store(Work.register(
                ORGANIZATION_ID,
                "기사만 정한 작업",
                REGISTRAR_ID,
                REGISTERED_AT,
                null,
                null,
                null,
                new PlannedSchedule(TECHNICIAN_ID, null, null)));
        WorkId timeOnly = fixture.workRepository.store(Work.register(
                ORGANIZATION_ID,
                "시간만 정한 작업",
                REGISTRAR_ID,
                REGISTERED_AT.plusSeconds(1),
                null,
                null,
                null,
                new PlannedSchedule(null, TEN, TWO_HOURS)));

        assertThat(service.get(query(FROM, TO)).backlog())
                .extracting(
                        BacklogWorkInfo::workId,
                        BacklogWorkInfo::plannedTechnician,
                        BacklogWorkInfo::plannedStartTime,
                        BacklogWorkInfo::plannedExpectedDuration,
                        BacklogWorkInfo::technicianMissing,
                        BacklogWorkInfo::timeMissing)
                .containsExactly(
                        tuple(technicianOnly.value(), KIM, null, null, false, true),
                        tuple(timeOnly.value(), null, TEN, TWO_HOURS, true, false));
    }

    @Test
    @DisplayName("등록한 지 방치 기준이 지난 대기함 작업을 방치로 표시한다")
    void marksStaleBacklog() {
        fixture.givenManager();
        Instant registeredAt = NOW.minus(Duration.ofHours(72));
        WorkId stale = fixture.workRepository.store(
                Work.register(ORGANIZATION_ID, "방치 작업", REGISTRAR_ID, registeredAt, null, null, null));
        WorkId fresh = fixture.workRepository.store(
                Work.register(ORGANIZATION_ID, "아직 아님", REGISTRAR_ID, registeredAt.plusSeconds(1), null, null, null));

        assertThat(service.get(query(FROM, TO)).backlog())
                .extracting(BacklogWorkInfo::workId, BacklogWorkInfo::stale)
                .containsExactly(tuple(stale.value(), true), tuple(fresh.value(), false));
    }

    @Test
    @DisplayName("구간이 없거나 뒤집혔거나 비었거나 31일을 넘으면 입력 오류다")
    void rejectsInvalidPeriod() {
        fixture.givenManager();

        for (Instant[] period : new Instant[][] {
            {null, TO},
            {FROM, null},
            {TO, FROM},
            {FROM, FROM},
            {FROM, FROM.plus(Duration.ofDays(31)).plusNanos(1_000)},
            {Instant.MIN, Instant.MIN.plus(Duration.ofDays(1))},
            {Instant.MAX.minus(Duration.ofDays(1)), Instant.MAX}
        }) {
            fixture.assertRejected(
                    () -> service.get(query(period[0], period[1])), ScheduleErrorCode.INVALID_WORK_INPUT);
        }
        assertThat(service.get(query(FROM, FROM.plus(Duration.ofDays(31)))).scheduledWorks())
                .isEmpty();
    }

    @Test
    @DisplayName("유형 조건의 식별자가 양수가 아니면 입력 오류다")
    void rejectsInvalidTypeIds() {
        fixture.givenManager();
        Set<Long> withNull = new HashSet<>();
        withNull.add(null);

        fixture.assertRejected(
                () -> service.get(query(FROM, TO, Set.of(0L), null)), ScheduleErrorCode.INVALID_WORK_INPUT);
        fixture.assertRejected(
                () -> service.get(query(FROM, TO, null, Set.of(-1L))), ScheduleErrorCode.INVALID_WORK_INPUT);
        fixture.assertRejected(
                () -> service.get(query(FROM, TO, withNull, null)), ScheduleErrorCode.INVALID_WORK_INPUT);
        assertThat(service.get(query(FROM, TO, Set.of(), Set.of())).technicians())
                .hasSize(3);
    }

    @Test
    @DisplayName("계정 → 조직 식별자 → 구성원 → 요청자 종류(기사 불가) → 구간 → 유형 조건 순서로 확인한다")
    void checksOrder() {
        assertThatThrownBy(() -> service.get(new GetTimetableQuery(null, 0L, null, null, Set.of(0L), null)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("accountId must not be null");
        fixture.assertRejected(
                () -> service.get(new GetTimetableQuery(ACCOUNT_ID, 0L, null, null, Set.of(0L), null)),
                ScheduleErrorCode.INVALID_WORK_INPUT);
        fixture.assertRejected(
                () -> service.get(query(null, null, Set.of(0L), null)), ScheduleErrorCode.NOT_ORGANIZATION_MEMBER);
        fixture.givenTechnician(TECHNICIAN_ID);
        fixture.assertRejected(
                () -> service.get(query(null, null, Set.of(0L), null)), ScheduleErrorCode.ACTION_NOT_ALLOWED);
    }

    private static GetTimetableQuery query(Instant from, Instant to) {
        return query(from, to, null, null);
    }

    private static GetTimetableQuery query(
            Instant from, Instant to, Set<Long> technicianTypeIds, Set<Long> workTypeIds) {
        return new GetTimetableQuery(ACCOUNT_ID, ORGANIZATION_ID.value(), from, to, technicianTypeIds, workTypeIds);
    }

    private static LabelInfo label(TypeLabel type) {
        return new LabelInfo(type.id(), type.name(), type.color());
    }
}
