package com.orbit.schedule.application.service;

import static com.orbit.schedule.application.service.ScheduleServiceFixture.ACCOUNT_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.ORGANIZATION_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.OTHER_ORGANIZATION_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.OTHER_TECHNICIAN_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TECHNICIAN_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TEN;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orbit.schedule.application.port.in.query.dto.GetWithdrawalBlockingWorksQuery;
import com.orbit.schedule.application.port.in.query.dto.WithdrawalBlockingWorksInfo;
import com.orbit.schedule.application.service.fake.FakeAccountTechnicianContractsPort;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.TechnicianId;
import com.orbit.schedule.domain.WorkStatus;

@DisplayName("탈퇴를 막는 기사 작업 조회")
class GetWithdrawalBlockingWorksServiceTest {

    private static final TechnicianId THIRD_TECHNICIAN_ID = new TechnicianId(5L);

    private final ScheduleServiceFixture fixture = new ScheduleServiceFixture();
    private final FakeAccountTechnicianContractsPort contracts = new FakeAccountTechnicianContractsPort();
    private final GetWithdrawalBlockingWorksService service =
            new GetWithdrawalBlockingWorksService(contracts, fixture.workRepository);

    @Test
    @DisplayName("활성 기사 계약마다 작업중·배정됨 작업 수를 모두 합쳐 센다")
    void countsInProgressAndPendingAcceptanceAcrossActiveContracts() {
        contracts.givenActive(ACCOUNT_ID, ORGANIZATION_ID, TECHNICIAN_ID);
        contracts.givenActive(ACCOUNT_ID, OTHER_ORGANIZATION_ID, OTHER_TECHNICIAN_ID);
        givenWork(ORGANIZATION_ID, TECHNICIAN_ID, WorkStatus.IN_PROGRESS, 0);
        givenWork(ORGANIZATION_ID, TECHNICIAN_ID, WorkStatus.PENDING_ACCEPT, 4);
        givenWork(OTHER_ORGANIZATION_ID, OTHER_TECHNICIAN_ID, WorkStatus.PENDING_ACCEPT, 0);

        WithdrawalBlockingWorksInfo info = service.get(query());

        assertThat(info).isEqualTo(new WithdrawalBlockingWorksInfo(1, 2));
    }

    @Test
    @DisplayName("작업전·완료·취소·대기함 작업은 탈퇴를 막지 않는다")
    void ignoresWorksThatDoNotBlockWithdrawal() {
        contracts.givenActive(ACCOUNT_ID, ORGANIZATION_ID, TECHNICIAN_ID);
        givenWork(ORGANIZATION_ID, TECHNICIAN_ID, WorkStatus.ACCEPTED, 0);
        givenWork(ORGANIZATION_ID, TECHNICIAN_ID, WorkStatus.COMPLETED, 4);
        givenWork(ORGANIZATION_ID, TECHNICIAN_ID, WorkStatus.CANCELED, 8);
        givenWork(ORGANIZATION_ID, TECHNICIAN_ID, WorkStatus.REGISTERED, 12);

        assertThat(service.get(query())).isEqualTo(new WithdrawalBlockingWorksInfo(0, 0));
    }

    @Test
    @DisplayName("비활성 기사 계약에 남은 작업중 작업과 다른 기사의 작업은 세지 않는다")
    void ignoresInactiveContractsAndOtherTechnicians() {
        contracts.givenInactive(ACCOUNT_ID, ORGANIZATION_ID, TECHNICIAN_ID);
        givenWork(ORGANIZATION_ID, TECHNICIAN_ID, WorkStatus.IN_PROGRESS, 0);
        contracts.givenActive(ACCOUNT_ID, OTHER_ORGANIZATION_ID, OTHER_TECHNICIAN_ID);
        givenWork(OTHER_ORGANIZATION_ID, THIRD_TECHNICIAN_ID, WorkStatus.IN_PROGRESS, 0);

        assertThat(service.get(query())).isEqualTo(new WithdrawalBlockingWorksInfo(0, 0));
    }

    @Test
    @DisplayName("기사 계약이 없는 계정은 막을 작업이 없다")
    void returnsNothingForAccountWithoutContracts() {
        assertThat(service.get(query())).isEqualTo(new WithdrawalBlockingWorksInfo(0, 0));
    }

    @Test
    @DisplayName("판정만 하고 작업을 저장하거나 기사를 잠그지 않는다")
    void neitherSavesNorLocks() {
        contracts.givenActive(ACCOUNT_ID, ORGANIZATION_ID, TECHNICIAN_ID);
        givenWork(ORGANIZATION_ID, TECHNICIAN_ID, WorkStatus.PENDING_ACCEPT, 0);

        service.get(query());

        assertThat(fixture.workRepository.saved()).isEmpty();
        assertThat(fixture.scheduleLock.locks()).isEmpty();
    }

    private void givenWork(
            OrganizationId organizationId, TechnicianId technicianId, WorkStatus status, int hoursAfterTen) {
        fixture.givenWork(organizationId, "작업", status, technicianId, TEN.plus(Duration.ofHours(hoursAfterTen)));
    }

    private static GetWithdrawalBlockingWorksQuery query() {
        return new GetWithdrawalBlockingWorksQuery(ACCOUNT_ID);
    }
}
