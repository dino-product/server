package com.orbit.schedule.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.application.service.fake.FakeLoadActorPort;
import com.orbit.schedule.application.service.fake.FakeLockTechnicianSchedulePort;
import com.orbit.schedule.application.service.fake.FakeStaffProfilesPort;
import com.orbit.schedule.application.service.fake.FakeTechnicianContractPort;
import com.orbit.schedule.application.service.fake.FakeTechnicianProfilesPort;
import com.orbit.schedule.application.service.fake.FakeWorkRepository;
import com.orbit.schedule.application.service.fake.FakeWorkTypeLabelsPort;
import com.orbit.schedule.application.service.fake.FakeWorkTypePort;
import com.orbit.schedule.domain.ActorRole;
import com.orbit.schedule.domain.CancellationReason;
import com.orbit.schedule.domain.CompletionReport;
import com.orbit.schedule.domain.CustomerInfo;
import com.orbit.schedule.domain.MembershipId;
import com.orbit.schedule.domain.Money;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.PaymentInfo;
import com.orbit.schedule.domain.PaymentMethod;
import com.orbit.schedule.domain.SiteAddress;
import com.orbit.schedule.domain.TechnicianId;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkId;
import com.orbit.schedule.domain.WorkSchedule;
import com.orbit.schedule.domain.WorkStatus;
import com.orbit.schedule.domain.WorkTypeId;
import com.orbit.shared.error.BusinessException;

/** 서비스 단위 테스트의 공용 준비물. 테스트마다 새로 만들어 fake 상태를 공유하지 않는다. */
final class ScheduleServiceFixture {

    static final Long ACCOUNT_ID = 7L;
    static final OrganizationId ORGANIZATION_ID = new OrganizationId(100L);
    static final OrganizationId OTHER_ORGANIZATION_ID = new OrganizationId(200L);
    static final MembershipId REGISTRAR_ID = new MembershipId(1L);
    /** {@link #givenManager()}가 만드는 관리자의 조직 소속. */
    static final MembershipId MANAGER_ID = new MembershipId(11L);
    /** 테스트 준비로 미리 배정·취소해 둔 관리자. 요청자({@link #MANAGER_ID})와 구분한다. */
    static final MembershipId SETUP_MANAGER_ID = new MembershipId(12L);

    static final TechnicianId TECHNICIAN_ID = new TechnicianId(3L);
    static final TechnicianId OTHER_TECHNICIAN_ID = new TechnicianId(4L);
    /** 요청 조직에서 선택할 수 있는 작업 유형. {@link #givenWork} 작업도 이 유형이다. */
    static final WorkTypeId WORK_TYPE_ID = new WorkTypeId(2L);

    static final Instant NOW = Instant.parse("2026-09-24T01:00:00Z");
    /**
     * 서비스 테스트가 등록하는 작업의 기본 등록 시각. 테스트가 쓰는 배정·응답 시각(지금부터 몇 시간 전)보다 앞서고, 대기함 방치 기준보다는 가까워 기본으로는
     * 방치가 아니다. 방치를 볼 때는 시각을 따로 정한다.
     */
    static final Instant REGISTERED_AT = NOW.minus(Duration.ofHours(6));

    static final Instant ASSIGNED_AT = NOW.minus(Duration.ofHours(2));
    static final Instant ACCEPTED_AT = ASSIGNED_AT.plus(Duration.ofMinutes(30));
    static final Instant TEN = Instant.parse("2026-09-25T01:00:00Z");
    static final Duration TWO_HOURS = Duration.ofHours(2);
    static final long UNKNOWN_WORK_ID = 999L;

    final FakeWorkRepository workRepository = new FakeWorkRepository();
    final FakeLoadActorPort actorPort = new FakeLoadActorPort();
    final FakeLockTechnicianSchedulePort scheduleLock = new FakeLockTechnicianSchedulePort(workRepository);
    final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    /** 요청 조직의 {@link #TECHNICIAN_ID}·{@link #OTHER_TECHNICIAN_ID}는 활성 기사 계약이다. */
    final FakeTechnicianContractPort technicianContracts = new FakeTechnicianContractPort();
    /** 요청 조직은 {@link #WORK_TYPE_ID}를 선택할 수 있다. */
    final FakeWorkTypePort workTypes = new FakeWorkTypePort();
    // 조회 화면의 표시 정보(기사·등록자·작업유형). 테스트가 넣지 않으면 찾지 못한다.
    final FakeTechnicianProfilesPort technicianProfiles = new FakeTechnicianProfilesPort();
    final FakeStaffProfilesPort staffProfiles = new FakeStaffProfilesPort();
    final FakeWorkTypeLabelsPort workTypeLabels = new FakeWorkTypeLabelsPort();

    ScheduleServiceFixture() {
        technicianContracts.givenActive(ORGANIZATION_ID, TECHNICIAN_ID);
        technicianContracts.givenActive(ORGANIZATION_ID, OTHER_TECHNICIAN_ID);
        workTypes.givenSelectable(ORGANIZATION_ID, WORK_TYPE_ID);
    }

    void givenManager() {
        givenManager(ActorRole.STAFF);
    }

    /** 요청자를 주어진 역할의 관리자({@link #MANAGER_ID})로 둔다. */
    void givenManager(ActorRole role) {
        actorPort.givenManager(ACCOUNT_ID, ORGANIZATION_ID, MANAGER_ID.value(), role);
    }

    /** 요청자를 {@link #givenWork} 작업을 등록한 직원({@link #REGISTRAR_ID})으로 둔다. */
    void givenRegistrarStaff() {
        actorPort.givenManager(ACCOUNT_ID, ORGANIZATION_ID, REGISTRAR_ID.value(), ActorRole.STAFF);
    }

    /** 요청자를 주어진 기사로 둔다. 담당 기사 본인 여부는 기사 식별자로 판단한다. */
    void givenTechnician(TechnicianId technicianId) {
        actorPort.givenTechnician(ACCOUNT_ID, ORGANIZATION_ID, technicianId.value());
    }

    /** 요청 조직의 작업을 주어진 상태까지 진행해 둔다. 대기함이 아니면 기사 {@link #TECHNICIAN_ID}의 {@link #TEN} 시작 2시간 일정으로 배정돼 있다. */
    WorkId givenWork(WorkStatus status) {
        return givenWork(ORGANIZATION_ID, "대상 작업", status, TECHNICIAN_ID, TEN);
    }

    /**
     * 주어진 상태까지 진행한 작업을 저장소에 둔다. 배정은 {@link #ASSIGNED_AT}, 수락·시작·완료는 {@link #ACCEPTED_AT}에 했고, 취소는 수락대기에서 해 일정이 남아 있다.
     */
    WorkId givenWork(
            OrganizationId organizationId, String name, WorkStatus status, TechnicianId technicianId, Instant start) {
        Work work = Work.register(
                organizationId,
                name,
                REGISTRAR_ID,
                REGISTERED_AT,
                WORK_TYPE_ID,
                new CustomerInfo("홍길동", "010-1234-5678", new SiteAddress("04043", "서울시", "3층")),
                new PaymentInfo(new Money(150_000L), PaymentMethod.ON_SITE_CARD));
        if (status != WorkStatus.REGISTERED) {
            work.assign(new WorkSchedule(technicianId, start, TWO_HOURS), ASSIGNED_AT, SETUP_MANAGER_ID);
        }
        if (status == WorkStatus.ACCEPTED || status == WorkStatus.IN_PROGRESS || status == WorkStatus.COMPLETED) {
            work.accept(ACCEPTED_AT);
        }
        if (status == WorkStatus.IN_PROGRESS || status == WorkStatus.COMPLETED) {
            work.start(ACCEPTED_AT);
        }
        if (status == WorkStatus.COMPLETED) {
            work.submitCompletionReport(new CompletionReport(null, null, null, null, null, null), ACCEPTED_AT);
        }
        if (status == WorkStatus.CANCELLED) {
            work.cancel(ACCEPTED_AT, SETUP_MANAGER_ID, CancellationReason.CUSTOMER_REQUEST, null);
        }
        return workRepository.store(work);
    }

    Work stored(WorkId id) {
        return workRepository.findInOrganization(ORGANIZATION_ID, id).orElseThrow();
    }

    Work singleSaved() {
        assertThat(workRepository.saved()).hasSize(1);
        return workRepository.saved().getFirst();
    }

    /** 오류 코드로 실패하고, 저장하지도 기사를 잠그지도 않았는지 확인한다. */
    void assertRejected(ThrowingCallable call, ScheduleErrorCode expected) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                .isEqualTo(expected));
        assertThat(workRepository.saved()).isEmpty();
        assertThat(scheduleLock.locks()).isEmpty();
    }
}
