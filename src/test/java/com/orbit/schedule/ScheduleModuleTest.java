package com.orbit.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.AssertablePublishedEvents;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.orbit.organization.OrganizationMemberLookup;
import com.orbit.organization.StaffMember;
import com.orbit.organization.TechnicianContract;
import com.orbit.organization.TechnicianContractLookup;
import com.orbit.organization.TechnicianMember;
import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.application.port.in.command.AcceptWorkUseCase;
import com.orbit.schedule.application.port.in.command.AssignWorkUseCase;
import com.orbit.schedule.application.port.in.command.CancelWorkUseCase;
import com.orbit.schedule.application.port.in.command.CreateWorkUseCase;
import com.orbit.schedule.application.port.in.command.ReassignWorkUseCase;
import com.orbit.schedule.application.port.in.command.RejectWorkUseCase;
import com.orbit.schedule.application.port.in.command.RescheduleWorkUseCase;
import com.orbit.schedule.application.port.in.command.StartWorkUseCase;
import com.orbit.schedule.application.port.in.command.SubmitCompletionReportUseCase;
import com.orbit.schedule.application.port.in.command.UnassignWorkUseCase;
import com.orbit.schedule.application.port.in.command.UpdateWorkDetailsUseCase;
import com.orbit.schedule.application.port.in.command.dto.AcceptWorkCommand;
import com.orbit.schedule.application.port.in.command.dto.AssignWorkCommand;
import com.orbit.schedule.application.port.in.command.dto.CancelWorkCommand;
import com.orbit.schedule.application.port.in.command.dto.CreateWorkCommand;
import com.orbit.schedule.application.port.in.command.dto.ReassignWorkCommand;
import com.orbit.schedule.application.port.in.command.dto.RejectWorkCommand;
import com.orbit.schedule.application.port.in.command.dto.RescheduleWorkCommand;
import com.orbit.schedule.application.port.in.command.dto.StartWorkCommand;
import com.orbit.schedule.application.port.in.command.dto.SubmitCompletionReportCommand;
import com.orbit.schedule.application.port.in.command.dto.UnassignWorkCommand;
import com.orbit.schedule.application.port.in.command.dto.UpdateWorkDetailsCommand;
import com.orbit.schedule.application.port.in.query.GetCompletionReportUseCase;
import com.orbit.schedule.application.port.in.query.GetMyScheduleUseCase;
import com.orbit.schedule.application.port.in.query.GetProgressBoardUseCase;
import com.orbit.schedule.application.port.in.query.GetTimetableUseCase;
import com.orbit.schedule.application.port.in.query.GetWorkDetailUseCase;
import com.orbit.schedule.application.port.in.query.GetWorkHistoryUseCase;
import com.orbit.schedule.application.port.in.query.SearchWorksUseCase;
import com.orbit.schedule.application.port.in.query.dto.GetCompletionReportQuery;
import com.orbit.schedule.application.port.in.query.dto.GetMyScheduleQuery;
import com.orbit.schedule.application.port.in.query.dto.GetProgressBoardQuery;
import com.orbit.schedule.application.port.in.query.dto.GetTimetableQuery;
import com.orbit.schedule.application.port.in.query.dto.GetWorkDetailQuery;
import com.orbit.schedule.application.port.in.query.dto.GetWorkHistoryQuery;
import com.orbit.schedule.application.port.in.query.dto.SearchWorksQuery;
import com.orbit.schedule.application.port.out.AccountTechnicianContract;
import com.orbit.schedule.application.port.out.AccountTechnicianContractsPort;
import com.orbit.schedule.application.port.out.LoadActorPort;
import com.orbit.schedule.application.port.out.LockTechnicianSchedulePort;
import com.orbit.schedule.application.port.out.TechnicianContractPort;
import com.orbit.schedule.application.port.out.WorkQueryPort;
import com.orbit.schedule.application.port.out.WorkRepository;
import com.orbit.schedule.application.port.out.WorkTypePort;
import com.orbit.schedule.domain.ActorRole;
import com.orbit.schedule.domain.CancellationReason;
import com.orbit.schedule.domain.ManagerActor;
import com.orbit.schedule.domain.MembershipId;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.RejectionReason;
import com.orbit.schedule.domain.TechnicianId;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkId;
import com.orbit.schedule.domain.WorkTypeId;
import com.orbit.shared.error.BusinessException;
import com.orbit.support.TestcontainersConfiguration;

@ApplicationModuleTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
class ScheduleModuleTest {

    private static final Instant REGISTERED_AT = Instant.parse("2020-01-01T00:00:00Z");

    private static final OrganizationId ORGANIZATION_ID = new OrganizationId(100L);

    @Autowired
    private WorkRepository workRepository;

    @Autowired
    private LoadActorPort loadActorPort;

    @Autowired
    private TechnicianContractPort technicianContractPort;

    @Autowired
    private AccountTechnicianContractsPort accountTechnicianContractsPort;

    @Autowired
    private WorkTypePort workTypePort;

    // organization 공개 계약. 조립 테스트는 schedule만 띄우므로 대체하며, 넣지 않으면 활성 구성원이 없다.
    @MockitoBean
    private OrganizationMemberLookup memberLookup;

    // organization 공개 계약. 넣지 않으면 활성 기사 계약이 없다.
    @MockitoBean
    private TechnicianContractLookup technicianContractLookup;

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
    private CancelWorkUseCase cancelWorkUseCase;

    @Autowired
    private AcceptWorkUseCase acceptWorkUseCase;

    @Autowired
    private RejectWorkUseCase rejectWorkUseCase;

    @Autowired
    private StartWorkUseCase startWorkUseCase;

    @Autowired
    private SubmitCompletionReportUseCase submitCompletionReportUseCase;

    @Autowired
    private GetWorkDetailUseCase getWorkDetailUseCase;

    @Autowired
    private GetCompletionReportUseCase getCompletionReportUseCase;

    @Autowired
    private GetTimetableUseCase getTimetableUseCase;

    @Autowired
    private SearchWorksUseCase searchWorksUseCase;

    @Autowired
    private GetProgressBoardUseCase getProgressBoardUseCase;

    @Autowired
    private GetMyScheduleUseCase getMyScheduleUseCase;

    @Autowired
    private GetWorkHistoryUseCase getWorkHistoryUseCase;

    @Autowired
    private WorkQueryPort workQueryPort;

    @Autowired
    private LockTechnicianSchedulePort lockTechnicianSchedulePort;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void assembledActorPortTranslatesActiveOrganizationMember() {
        when(memberLookup.findActiveMember(1L, ORGANIZATION_ID.value()))
                .thenReturn(Optional.of(new StaffMember(11L, ORGANIZATION_ID.value(), true)));

        assertThat(loadActorPort.findActiveActor(1L, ORGANIZATION_ID))
                .contains(new ManagerActor(new MembershipId(11L), ORGANIZATION_ID, ActorRole.OWNER));
    }

    @Test
    void assembledActorPortIsEmptyWithoutActiveOrganizationMember() {
        assertThat(loadActorPort.findActiveActor(1L, ORGANIZATION_ID)).isEmpty();
    }

    @Test
    void assembledWorkRepositoryStoresAndFindsWork() {
        WorkId id = workRepository
                .save(Work.register(ORGANIZATION_ID, "모듈 작업", new MembershipId(1L), REGISTERED_AT, null, null, null))
                .id()
                .orElseThrow();

        assertThat(workRepository.findInOrganization(ORGANIZATION_ID, id))
                .hasValueSatisfying(work -> assertThat(work.name()).isEqualTo("모듈 작업"));
    }

    @Test
    void assembledCreateWorkUseCaseDeniesAccountWithoutActiveOrganizationMember() {
        assertThatThrownBy(() -> createWorkUseCase.create(new CreateWorkCommand(
                        1L,
                        ORGANIZATION_ID.value(),
                        "모듈 작업",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        false)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ScheduleErrorCode.NOT_ORGANIZATION_MEMBER));
    }

    @Test
    void assembledUpdateWorkDetailsUseCaseDeniesAccountWithoutActiveOrganizationMember() {
        assertThatThrownBy(() -> updateWorkDetailsUseCase.update(new UpdateWorkDetailsCommand(
                        1L,
                        ORGANIZATION_ID.value(),
                        1L,
                        "모듈 작업",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ScheduleErrorCode.NOT_ORGANIZATION_MEMBER));
    }

    @Test
    void assembledAssignWorkUseCaseDeniesAccountWithoutActiveOrganizationMember() {
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
    void assembledAssignmentChangeUseCasesDenyAccountWithoutActiveOrganizationMember() {
        Instant startTime = Instant.parse("2026-09-25T01:00:00Z");
        assertDenied(() -> reassignWorkUseCase.reassign(
                new ReassignWorkCommand(1L, ORGANIZATION_ID.value(), 1L, 4L, startTime, Duration.ofHours(2), false)));
        assertDenied(() -> rescheduleWorkUseCase.reschedule(
                new RescheduleWorkCommand(1L, ORGANIZATION_ID.value(), 1L, startTime, Duration.ofHours(2), false)));
        assertDenied(() -> unassignWorkUseCase.unassign(new UnassignWorkCommand(1L, ORGANIZATION_ID.value(), 1L, 0L)));
        assertDenied(() -> cancelWorkUseCase.cancel(
                new CancelWorkCommand(1L, ORGANIZATION_ID.value(), 1L, CancellationReason.CUSTOMER_REQUEST, null)));
    }

    @Test
    void assembledTechnicianUseCasesDenyAccountWithoutActiveOrganizationMember() {
        assertDenied(() -> acceptWorkUseCase.accept(new AcceptWorkCommand(1L, ORGANIZATION_ID.value(), 1L, 1)));
        assertDenied(() -> rejectWorkUseCase.reject(
                new RejectWorkCommand(1L, ORGANIZATION_ID.value(), 1L, 1, RejectionReason.OTHER, "기타 사유")));
        assertDenied(() -> startWorkUseCase.start(new StartWorkCommand(1L, ORGANIZATION_ID.value(), 1L, 1)));
        assertDenied(() -> submitCompletionReportUseCase.submit(new SubmitCompletionReportCommand(
                1L, ORGANIZATION_ID.value(), 1L, 1, null, null, null, null, null, null)));
    }

    @Test
    void assembledQueryUseCasesDenyAccountWithoutActiveOrganizationMember() {
        assertDenied(() -> getWorkDetailUseCase.get(new GetWorkDetailQuery(1L, ORGANIZATION_ID.value(), 1L)));
        assertDenied(
                () -> getCompletionReportUseCase.get(new GetCompletionReportQuery(1L, ORGANIZATION_ID.value(), 1L)));
        Instant from = Instant.parse("2026-09-25T00:00:00Z");
        assertDenied(() -> getTimetableUseCase.get(
                new GetTimetableQuery(1L, ORGANIZATION_ID.value(), from, from.plus(Duration.ofDays(1)), null, null)));
        assertDenied(
                () -> searchWorksUseCase.search(new SearchWorksQuery(1L, ORGANIZATION_ID.value(), null, null, null)));
        assertDenied(() -> getProgressBoardUseCase.get(
                new GetProgressBoardQuery(1L, ORGANIZATION_ID.value(), from, from.plus(Duration.ofDays(1)))));
        assertDenied(() -> getMyScheduleUseCase.get(
                new GetMyScheduleQuery(1L, ORGANIZATION_ID.value(), from, from.plus(Duration.ofDays(1)))));
        assertDenied(() -> getWorkHistoryUseCase.get(
                new GetWorkHistoryQuery(1L, ORGANIZATION_ID.value(), 3L, from, from.plus(Duration.ofDays(1)))));
    }

    @Test
    void assembledWorkQueryPortReadsSavedWorks() {
        Work saved = workRepository.save(
                Work.register(ORGANIZATION_ID, "대기 작업", new MembershipId(1L), REGISTERED_AT, null, null, null));

        assertThat(workQueryPort.listBacklog(ORGANIZATION_ID))
                .extracting(work -> work.id().orElseThrow())
                .contains(saved.id().orElseThrow());
    }

    @Test
    void assembledTechnicianContractPortsUseOrganizationContract() {
        when(technicianContractLookup.existsActive(100L, 3L)).thenReturn(true);
        when(technicianContractLookup.listByAccount(7L)).thenReturn(List.of(new TechnicianContract(3L, 100L, false)));

        assertThat(technicianContractPort.isActive(ORGANIZATION_ID, new TechnicianId(3L)))
                .isTrue();
        assertThat(technicianContractPort.isActive(ORGANIZATION_ID, new TechnicianId(4L)))
                .isFalse();
        assertThat(accountTechnicianContractsPort.listByAccount(7L))
                .containsExactly(new AccountTechnicianContract(new TechnicianId(3L), ORGANIZATION_ID, false));
    }

    @Test
    void assembledWorkTypePortDeniesUntilOrganizationContract() {
        assertThat(workTypePort.isSelectable(ORGANIZATION_ID, new WorkTypeId(2L)))
                .isFalse();
    }

    @Test
    void assembledTechnicianScheduleLockJoinsTheServiceTransaction() {
        // 저장소와 다른 연결이면 어댑터가 거부하므로, 예외 없이 끝나면 자동 설정된 JdbcTemplate이 트랜잭션 연결을 쓴다는 뜻이다.
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> lockTechnicianSchedulePort.lock(ORGANIZATION_ID, new TechnicianId(3L)));
    }

    @Test
    void assembledAssignAndAcceptPublishPublicEvents(AssertablePublishedEvents events) {
        // 계정 1의 소속이 작업을 등록하고 배정한다.
        long registrarMembershipId = 11L;
        when(memberLookup.findActiveMember(1L, ORGANIZATION_ID.value()))
                .thenReturn(Optional.of(new StaffMember(registrarMembershipId, ORGANIZATION_ID.value(), true)));
        when(memberLookup.findActiveMember(2L, ORGANIZATION_ID.value()))
                .thenReturn(Optional.of(new TechnicianMember(31L, ORGANIZATION_ID.value())));
        when(technicianContractLookup.existsActive(ORGANIZATION_ID.value(), 31L))
                .thenReturn(true);
        Long workId = workRepository
                .save(Work.register(
                        ORGANIZATION_ID,
                        "알림 작업",
                        new MembershipId(registrarMembershipId),
                        REGISTERED_AT,
                        null,
                        null,
                        null))
                .id()
                .orElseThrow()
                .value();
        Instant startTime = Instant.parse("2026-09-25T01:00:00Z");

        // 겹침을 확인한 요청으로 보내, 다른 테스트가 같은 기사에게 남긴 작업과 관계없이 반영되게 한다.
        assertThat(assignWorkUseCase
                        .assign(new AssignWorkCommand(
                                1L, ORGANIZATION_ID.value(), workId, 31L, startTime, Duration.ofHours(2), true))
                        .applied())
                .isTrue();
        acceptWorkUseCase.accept(new AcceptWorkCommand(2L, ORGANIZATION_ID.value(), workId, 1));

        assertThat(events.ofType(WorkAssigned.class)).singleElement().satisfies(event -> {
            assertThat(event.workId()).isEqualTo(workId);
            assertThat(event.organizationId()).isEqualTo(ORGANIZATION_ID.value());
            assertThat(event.technicianId()).isEqualTo(31L);
            assertThat(event.workName()).isEqualTo("알림 작업");
            assertThat(event.scheduledStartAt()).isEqualTo(startTime);
        });
        assertThat(events.ofType(WorkAccepted.class)).singleElement().satisfies(event -> {
            assertThat(event.workId()).isEqualTo(workId);
            assertThat(event.registrarMembershipId()).isEqualTo(registrarMembershipId);
            assertThat(event.technicianId()).isEqualTo(31L);
        });
    }

    private static void assertDenied(ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                .isEqualTo(ScheduleErrorCode.NOT_ORGANIZATION_MEMBER));
    }
}
