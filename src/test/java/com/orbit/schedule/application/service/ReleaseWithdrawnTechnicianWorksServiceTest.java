package com.orbit.schedule.application.service;

import static com.orbit.schedule.application.service.ScheduleServiceFixture.ACCOUNT_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.NOW;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.ORGANIZATION_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.OTHER_ORGANIZATION_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.OTHER_TECHNICIAN_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TECHNICIAN_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.application.port.in.command.dto.ReleaseWithdrawnTechnicianWorksCommand;
import com.orbit.schedule.application.port.out.TechnicianScheduleBusyException;
import com.orbit.schedule.application.service.fake.FakeAccountTechnicianContractsPort;
import com.orbit.schedule.application.service.fake.FakeLockTechnicianSchedulePort;
import com.orbit.schedule.domain.AssignmentEndReason;
import com.orbit.schedule.domain.AssignmentEnding;
import com.orbit.schedule.domain.AssignmentResult;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.TechnicianId;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkId;
import com.orbit.schedule.domain.WorkStatus;

@DisplayName("탈퇴한 기사의 예정 작업 대기함 반환")
class ReleaseWithdrawnTechnicianWorksServiceTest {

    private static final TechnicianId THIRD_TECHNICIAN_ID = new TechnicianId(5L);

    private final ScheduleServiceFixture fixture = new ScheduleServiceFixture();
    private final FakeAccountTechnicianContractsPort contracts = new FakeAccountTechnicianContractsPort();
    private final ReleaseWithdrawnTechnicianWorksService service = new ReleaseWithdrawnTechnicianWorksService(
            contracts, fixture.workRepository, fixture.scheduleLock, fixture.clock);

    @Test
    @DisplayName("배정됨·작업전 작업을 처리자 없이 대기함으로 돌리고 작업중·완료 작업은 그대로 둔다")
    void releasesScheduledWorksWithoutProcessor() {
        contracts.givenActive(ACCOUNT_ID, ORGANIZATION_ID, TECHNICIAN_ID);
        WorkId pending = givenWork(ORGANIZATION_ID, TECHNICIAN_ID, WorkStatus.PENDING_ACCEPTANCE, 0);
        WorkId accepted = givenWork(ORGANIZATION_ID, TECHNICIAN_ID, WorkStatus.ACCEPTED, 4);
        WorkId inProgress = givenWork(ORGANIZATION_ID, TECHNICIAN_ID, WorkStatus.IN_PROGRESS, 8);
        WorkId completed = givenWork(ORGANIZATION_ID, TECHNICIAN_ID, WorkStatus.COMPLETED, 12);

        service.release(command());

        assertThat(fixture.workRepository.saved())
                .extracting(work -> work.id().orElseThrow(), Work::status)
                .containsExactlyInAnyOrder(
                        tuple(pending, WorkStatus.REGISTERED), tuple(accepted, WorkStatus.REGISTERED));
        assertThat(fixture.stored(pending).assignmentHistory().getLast().result())
                .isEqualTo(AssignmentResult.WITHDRAWN);
        assertThat(fixture.stored(accepted).assignmentHistory().getLast().result())
                .isEqualTo(AssignmentResult.ACCEPTED);
        assertThat(fixture.stored(accepted).assignmentHistory().getLast().ending())
                .contains(new AssignmentEnding(NOW, null, AssignmentEndReason.ACCOUNT_WITHDRAWN));
        assertThat(fixture.stored(accepted).schedule()).isEmpty();
        assertThat(fixture.stored(inProgress).status()).isEqualTo(WorkStatus.IN_PROGRESS);
        assertThat(fixture.stored(completed).status()).isEqualTo(WorkStatus.COMPLETED);
    }

    @Test
    @DisplayName("organization이 먼저 비활성화한 계약까지 모든 조직의 기사 계약에서 돌린다")
    void releasesAcrossEveryContractIncludingInactive() {
        contracts.givenInactive(ACCOUNT_ID, ORGANIZATION_ID, TECHNICIAN_ID);
        contracts.givenActive(ACCOUNT_ID, OTHER_ORGANIZATION_ID, OTHER_TECHNICIAN_ID);
        WorkId first = givenWork(ORGANIZATION_ID, TECHNICIAN_ID, WorkStatus.ACCEPTED, 0);
        WorkId second = givenWork(OTHER_ORGANIZATION_ID, OTHER_TECHNICIAN_ID, WorkStatus.PENDING_ACCEPTANCE, 0);

        service.release(command());

        assertThat(fixture.stored(first).status()).isEqualTo(WorkStatus.REGISTERED);
        assertThat(fixture.workRepository
                        .findInOrganization(OTHER_ORGANIZATION_ID, second)
                        .orElseThrow()
                        .status())
                .isEqualTo(WorkStatus.REGISTERED);
    }

    @Test
    @DisplayName("다른 기사의 작업은 같은 조직이어도 건드리지 않는다")
    void leavesOtherTechniciansAlone() {
        contracts.givenActive(ACCOUNT_ID, ORGANIZATION_ID, TECHNICIAN_ID);
        WorkId other = givenWork(ORGANIZATION_ID, THIRD_TECHNICIAN_ID, WorkStatus.ACCEPTED, 0);

        service.release(command());

        assertThat(fixture.stored(other).status()).isEqualTo(WorkStatus.ACCEPTED);
        assertThat(fixture.workRepository.saved()).isEmpty();
    }

    @Test
    @DisplayName("같은 탈퇴를 다시 처리하면 이미 대기함인 작업은 건너뛰고 성공한다")
    void isIdempotentOnRedelivery() {
        contracts.givenActive(ACCOUNT_ID, ORGANIZATION_ID, TECHNICIAN_ID);
        WorkId accepted = givenWork(ORGANIZATION_ID, TECHNICIAN_ID, WorkStatus.ACCEPTED, 0);
        service.release(command());
        int savedAfterFirst = fixture.workRepository.saved().size();

        service.release(command());

        assertThat(fixture.workRepository.saved()).hasSize(savedAfterFirst);
        assertThat(fixture.stored(accepted).assignmentHistory()).hasSize(1);
    }

    @Test
    @DisplayName("기사 계약마다 활성 작업을 읽기 전에 조직·기사 계약 순으로 잠근다")
    void locksEachContractInOrderBeforeReading() {
        contracts.givenActive(ACCOUNT_ID, OTHER_ORGANIZATION_ID, OTHER_TECHNICIAN_ID);
        contracts.givenInactive(ACCOUNT_ID, ORGANIZATION_ID, TECHNICIAN_ID);

        service.release(command());

        assertThat(fixture.scheduleLock.locks())
                .containsExactly(
                        new FakeLockTechnicianSchedulePort.Lock(ORGANIZATION_ID, TECHNICIAN_ID, 0),
                        new FakeLockTechnicianSchedulePort.Lock(OTHER_ORGANIZATION_ID, OTHER_TECHNICIAN_ID, 1));
    }

    @Test
    @DisplayName("기사 잠금 대기 한도를 넘기면 409로 실패해 재전송을 기다린다")
    void failsWhenTechnicianIsBusy() {
        contracts.givenActive(ACCOUNT_ID, ORGANIZATION_ID, TECHNICIAN_ID);
        givenWork(ORGANIZATION_ID, TECHNICIAN_ID, WorkStatus.ACCEPTED, 0);
        fixture.scheduleLock.failWith(new TechnicianScheduleBusyException(null));

        fixture.assertRejected(() -> service.release(command()), ScheduleErrorCode.TECHNICIAN_SCHEDULE_BUSY);
    }

    @Test
    @DisplayName("기사 계약이 없는 계정은 아무것도 하지 않는다")
    void doesNothingWithoutContracts() {
        service.release(command());

        assertThat(fixture.workRepository.saved()).isEmpty();
        assertThat(fixture.scheduleLock.locks()).isEmpty();
    }

    private WorkId givenWork(
            OrganizationId organizationId, TechnicianId technicianId, WorkStatus status, int hoursAfterTen) {
        return fixture.givenWork(organizationId, "작업", status, technicianId, TEN.plus(Duration.ofHours(hoursAfterTen)));
    }

    private static ReleaseWithdrawnTechnicianWorksCommand command() {
        return new ReleaseWithdrawnTechnicianWorksCommand(ACCOUNT_ID);
    }
}
