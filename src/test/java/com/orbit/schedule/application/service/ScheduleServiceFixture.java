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
import com.orbit.schedule.application.service.fake.FakeWorkRepository;
import com.orbit.schedule.domain.ActorRole;
import com.orbit.schedule.domain.CompletionReport;
import com.orbit.schedule.domain.CustomerInfo;
import com.orbit.schedule.domain.MembershipId;
import com.orbit.schedule.domain.Money;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.PaymentInfo;
import com.orbit.schedule.domain.PaymentMethod;
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

    static final MembershipId TECHNICIAN_ID = new MembershipId(3L);
    static final MembershipId OTHER_TECHNICIAN_ID = new MembershipId(4L);
    static final Instant NOW = Instant.parse("2026-09-24T01:00:00Z");
    static final Instant ASSIGNED_AT = NOW.minus(Duration.ofHours(2));
    static final Instant ACCEPTED_AT = ASSIGNED_AT.plus(Duration.ofMinutes(30));
    static final Instant TEN = Instant.parse("2026-09-25T01:00:00Z");
    static final Duration TWO_HOURS = Duration.ofHours(2);
    static final long UNKNOWN_WORK_ID = 999L;

    final FakeWorkRepository workRepository = new FakeWorkRepository();
    final FakeLoadActorPort actorPort = new FakeLoadActorPort();
    final FakeLockTechnicianSchedulePort scheduleLock = new FakeLockTechnicianSchedulePort(workRepository);
    final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    void givenManager() {
        givenActor(ActorRole.STAFF);
    }

    void givenActor(ActorRole role) {
        actorPort.givenActor(ACCOUNT_ID, ORGANIZATION_ID, MANAGER_ID.value(), role);
    }

    /** 요청 조직의 작업을 주어진 상태까지 진행해 둔다. 대기함이 아니면 기사 {@link #TECHNICIAN_ID}의 {@link #TEN} 시작 2시간 일정으로 배정돼 있다. */
    WorkId givenWork(WorkStatus status) {
        return givenWork(ORGANIZATION_ID, "대상 작업", status, TECHNICIAN_ID, TEN);
    }

    /**
     * 주어진 상태까지 진행한 작업을 저장소에 둔다. 배정은 {@link #ASSIGNED_AT}, 수락은 {@link #ACCEPTED_AT}에 했고, 취소는 수락대기에서 해 일정이 남아 있다.
     */
    WorkId givenWork(
            OrganizationId organizationId, String name, WorkStatus status, MembershipId technicianId, Instant start) {
        Work work = Work.register(
                organizationId,
                name,
                REGISTRAR_ID,
                new WorkTypeId(2L),
                new CustomerInfo("홍길동", "010-1234-5678", "서울시"),
                new PaymentInfo(new Money(150_000L), PaymentMethod.ON_SITE_CARD));
        if (status != WorkStatus.REGISTERED) {
            work.assign(new WorkSchedule(technicianId, start, TWO_HOURS), ASSIGNED_AT, SETUP_MANAGER_ID);
        }
        if (status == WorkStatus.ACCEPTED || status == WorkStatus.IN_PROGRESS || status == WorkStatus.COMPLETED) {
            work.accept(ACCEPTED_AT);
        }
        if (status == WorkStatus.IN_PROGRESS || status == WorkStatus.COMPLETED) {
            work.start();
        }
        if (status == WorkStatus.COMPLETED) {
            work.submitCompletionReport(new CompletionReport(null, null, null, null, null, null));
        }
        if (status == WorkStatus.CANCELLED) {
            work.cancel(ACCEPTED_AT, SETUP_MANAGER_ID);
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
