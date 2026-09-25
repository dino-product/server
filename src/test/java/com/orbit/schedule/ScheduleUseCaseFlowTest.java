package com.orbit.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.orbit.schedule.application.port.in.command.AssignWorkUseCase;
import com.orbit.schedule.application.port.in.command.CreateWorkUseCase;
import com.orbit.schedule.application.port.in.command.ReassignWorkUseCase;
import com.orbit.schedule.application.port.in.command.RescheduleWorkUseCase;
import com.orbit.schedule.application.port.in.command.UnassignWorkUseCase;
import com.orbit.schedule.application.port.in.command.dto.AssignWorkCommand;
import com.orbit.schedule.application.port.in.command.dto.CreateWorkCommand;
import com.orbit.schedule.application.port.in.command.dto.ReassignWorkCommand;
import com.orbit.schedule.application.port.in.command.dto.RescheduleWorkCommand;
import com.orbit.schedule.application.port.in.command.dto.ScheduleChangeInfo;
import com.orbit.schedule.application.port.in.command.dto.UnassignWorkCommand;
import com.orbit.schedule.application.port.out.LoadActorPort;
import com.orbit.schedule.application.port.out.WorkRepository;
import com.orbit.schedule.domain.Actor;
import com.orbit.schedule.domain.ActorRole;
import com.orbit.schedule.domain.AssignmentEndReason;
import com.orbit.schedule.domain.AssignmentEnding;
import com.orbit.schedule.domain.AssignmentHistory;
import com.orbit.schedule.domain.AssignmentResult;
import com.orbit.schedule.domain.MembershipId;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkId;
import com.orbit.schedule.domain.WorkStatus;
import com.orbit.support.TestcontainersConfiguration;

/**
 * 실제로 조립된 유즈케이스가 트랜잭션·기사 잠금을 거쳐 끝까지 성공하는지 확인한다. organization 계약이 없어 행위자 포트만 직원을 돌려주는 테스트 구현으로 바꾼다.
 */
@ApplicationModuleTest
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, ScheduleUseCaseFlowTest.ManagerActors.class})
@Testcontainers(disabledWithoutDocker = true)
class ScheduleUseCaseFlowTest {

    private static final long ACCOUNT_ID = 1L;
    private static final OrganizationId ORGANIZATION_ID = new OrganizationId(300L);
    private static final long TECHNICIAN = 31L;
    private static final long OTHER_TECHNICIAN = 32L;
    // 두 테스트가 같은 메모리 저장소를 쓰므로, 겹침 테스트는 흐름 테스트와 다른 기사를 써서 실행 순서와 관계없이 결과가 같게 한다.
    private static final long CONFLICT_TECHNICIAN = 41L;
    private static final Instant TEN = Instant.parse("2030-01-02T01:00:00Z");
    private static final Duration TWO_HOURS = Duration.ofHours(2);
    private static final MembershipId MANAGER = new MembershipId(11L);

    @Autowired
    private CreateWorkUseCase createWorkUseCase;

    @Autowired
    private AssignWorkUseCase assignWorkUseCase;

    @Autowired
    private ReassignWorkUseCase reassignWorkUseCase;

    @Autowired
    private RescheduleWorkUseCase rescheduleWorkUseCase;

    @Autowired
    private UnassignWorkUseCase unassignWorkUseCase;

    @Autowired
    private WorkRepository workRepository;

    @Test
    void assembledUseCasesRunThroughTransactionAndTechnicianLock() {
        long workId = create("흐름 작업");

        assertThat(assignWorkUseCase
                        .assign(new AssignWorkCommand(
                                ACCOUNT_ID, ORGANIZATION_ID.value(), workId, TECHNICIAN, TEN, TWO_HOURS, false))
                        .applied())
                .isTrue();
        assertThat(reassignWorkUseCase
                        .reassign(new ReassignWorkCommand(
                                ACCOUNT_ID, ORGANIZATION_ID.value(), workId, OTHER_TECHNICIAN, TEN, TWO_HOURS, false))
                        .applied())
                .isTrue();
        assertThat(rescheduleWorkUseCase
                        .reschedule(new RescheduleWorkCommand(
                                ACCOUNT_ID,
                                ORGANIZATION_ID.value(),
                                workId,
                                TEN.plus(Duration.ofHours(3)),
                                TWO_HOURS,
                                false))
                        .applied())
                .isTrue();
        unassignWorkUseCase.unassign(new UnassignWorkCommand(ACCOUNT_ID, ORGANIZATION_ID.value(), workId));

        Work stored = workRepository
                .findInOrganization(ORGANIZATION_ID, new WorkId(workId))
                .orElseThrow();
        assertThat(stored.status()).isEqualTo(WorkStatus.REGISTERED);
        // 배정·재배정·일정 변경이 각각 이력을 남기고, 응답 전에 다음 조치로 회수됐다. 회수 방식과 처리자는 종료 기록에 남는다.
        assertThat(stored.assignmentHistory())
                .extracting(
                        AssignmentHistory::result,
                        history ->
                                history.ending().map(AssignmentEnding::reason).orElse(null),
                        history ->
                                history.ending().map(AssignmentEnding::endedBy).orElse(null))
                .containsExactly(
                        Tuple.tuple(AssignmentResult.WITHDRAWN, AssignmentEndReason.REASSIGNED, MANAGER),
                        Tuple.tuple(AssignmentResult.WITHDRAWN, AssignmentEndReason.RESCHEDULED, MANAGER),
                        Tuple.tuple(AssignmentResult.WITHDRAWN, AssignmentEndReason.UNASSIGNED, MANAGER));
    }

    @Test
    void assembledAssignWithholdsUnconfirmedConflict() {
        long existing = create("기존 작업");
        long target = create("겹치는 작업");
        assignWorkUseCase.assign(new AssignWorkCommand(
                ACCOUNT_ID, ORGANIZATION_ID.value(), existing, CONFLICT_TECHNICIAN, TEN, TWO_HOURS, false));

        ScheduleChangeInfo result = assignWorkUseCase.assign(new AssignWorkCommand(
                ACCOUNT_ID, ORGANIZATION_ID.value(), target, CONFLICT_TECHNICIAN, TEN, TWO_HOURS, false));

        assertThat(result.applied()).isFalse();
        assertThat(result.conflicts())
                .extracting(ScheduleChangeInfo.ConflictingWork::workId)
                .containsExactly(existing);
        assertThat(workRepository
                        .findInOrganization(ORGANIZATION_ID, new WorkId(target))
                        .orElseThrow()
                        .status())
                .isEqualTo(WorkStatus.REGISTERED);
    }

    private long create(String name) {
        return createWorkUseCase
                .create(new CreateWorkCommand(
                        ACCOUNT_ID, ORGANIZATION_ID.value(), name, null, null, null, null, null, null))
                .workId();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ManagerActors {

        @Bean
        @Primary
        LoadActorPort managerActorPort() {
            return (accountId, organizationId) -> Optional.of(new Actor(MANAGER, organizationId, ActorRole.STAFF));
        }
    }
}
