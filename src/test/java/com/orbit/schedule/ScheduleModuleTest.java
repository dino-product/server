package com.orbit.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.application.port.in.command.AssignWorkUseCase;
import com.orbit.schedule.application.port.in.command.CreateWorkUseCase;
import com.orbit.schedule.application.port.in.command.ReassignWorkUseCase;
import com.orbit.schedule.application.port.in.command.RescheduleWorkUseCase;
import com.orbit.schedule.application.port.in.command.UnassignWorkUseCase;
import com.orbit.schedule.application.port.in.command.UpdateWorkDetailsUseCase;
import com.orbit.schedule.application.port.in.command.dto.AssignWorkCommand;
import com.orbit.schedule.application.port.in.command.dto.CreateWorkCommand;
import com.orbit.schedule.application.port.in.command.dto.ReassignWorkCommand;
import com.orbit.schedule.application.port.in.command.dto.RescheduleWorkCommand;
import com.orbit.schedule.application.port.in.command.dto.UnassignWorkCommand;
import com.orbit.schedule.application.port.in.command.dto.UpdateWorkDetailsCommand;
import com.orbit.schedule.application.port.out.LoadActorPort;
import com.orbit.schedule.application.port.out.LockTechnicianSchedulePort;
import com.orbit.schedule.application.port.out.WorkRepository;
import com.orbit.schedule.domain.MembershipId;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkId;
import com.orbit.shared.error.BusinessException;
import com.orbit.support.TestcontainersConfiguration;

@ApplicationModuleTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
class ScheduleModuleTest {

    private static final OrganizationId ORGANIZATION_ID = new OrganizationId(100L);

    @Autowired
    private WorkRepository workRepository;

    @Autowired
    private LoadActorPort loadActorPort;

    @Autowired
    private CreateWorkUseCase createWorkUseCase;

    @Autowired
    private UpdateWorkDetailsUseCase updateWorkDetailsUseCase;

    @Autowired
    private AssignWorkUseCase assignWorkUseCase;

    @Autowired
    private ReassignWorkUseCase reassignWorkUseCase;

    @Autowired
    private RescheduleWorkUseCase rescheduleWorkUseCase;

    @Autowired
    private UnassignWorkUseCase unassignWorkUseCase;

    @Autowired
    private LockTechnicianSchedulePort lockTechnicianSchedulePort;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void assembledActorPortDeniesEveryAccountUntilOrganizationIsWired() {
        assertThat(loadActorPort.findActiveActor(1L, ORGANIZATION_ID)).isEmpty();
    }

    @Test
    void assembledWorkRepositoryStoresAndFindsWork() {
        WorkId id = workRepository
                .save(Work.register(ORGANIZATION_ID, "모듈 작업", new MembershipId(1L), null, null, null))
                .id()
                .orElseThrow();

        assertThat(workRepository.findById(id))
                .hasValueSatisfying(work -> assertThat(work.name()).isEqualTo("모듈 작업"));
    }

    @Test
    void assembledCreateWorkUseCaseDeniesEveryoneUntilOrganizationIsWired() {
        assertThatThrownBy(() -> createWorkUseCase.create(new CreateWorkCommand(
                        1L, ORGANIZATION_ID.value(), "모듈 작업", null, null, null, null, null, null)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ScheduleErrorCode.NOT_ORGANIZATION_MEMBER));
    }

    @Test
    void assembledUpdateWorkDetailsUseCaseDeniesEveryoneUntilOrganizationIsWired() {
        assertThatThrownBy(() -> updateWorkDetailsUseCase.update(new UpdateWorkDetailsCommand(
                        1L, ORGANIZATION_ID.value(), 1L, "모듈 작업", null, null, null, null, null, null)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ScheduleErrorCode.NOT_ORGANIZATION_MEMBER));
    }

    @Test
    void assembledAssignWorkUseCaseDeniesEveryoneUntilOrganizationIsWired() {
        assertThatThrownBy(() -> assignWorkUseCase.assign(new AssignWorkCommand(
                        1L,
                        ORGANIZATION_ID.value(),
                        1L,
                        3L,
                        Instant.parse("2026-09-25T01:00:00Z"),
                        Duration.ofHours(2),
                        false)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ScheduleErrorCode.NOT_ORGANIZATION_MEMBER));
    }

    @Test
    void assembledAssignmentChangeUseCasesDenyEveryoneUntilOrganizationIsWired() {
        Instant startTime = Instant.parse("2026-09-25T01:00:00Z");
        assertDenied(() -> reassignWorkUseCase.reassign(
                new ReassignWorkCommand(1L, ORGANIZATION_ID.value(), 1L, 4L, startTime, Duration.ofHours(2), false)));
        assertDenied(() -> rescheduleWorkUseCase.reschedule(
                new RescheduleWorkCommand(1L, ORGANIZATION_ID.value(), 1L, startTime, Duration.ofHours(2), false)));
        assertDenied(() -> unassignWorkUseCase.unassign(new UnassignWorkCommand(1L, ORGANIZATION_ID.value(), 1L)));
    }

    @Test
    void assembledTechnicianScheduleLockJoinsTheServiceTransaction() {
        // 저장소와 다른 연결이면 어댑터가 거부하므로, 예외 없이 끝나면 자동 설정된 JdbcTemplate이 트랜잭션 연결을 쓴다는 뜻이다.
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> lockTechnicianSchedulePort.lock(ORGANIZATION_ID, new MembershipId(3L)));
    }

    private static void assertDenied(ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                .isEqualTo(ScheduleErrorCode.NOT_ORGANIZATION_MEMBER));
    }
}
