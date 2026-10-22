package com.orbit.organization;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.orbit.organization.application.port.in.command.ApproveParticipationRequestsUseCase;
import com.orbit.organization.application.port.in.command.ChangeParticipantRoleUseCase;
import com.orbit.organization.application.port.in.command.CreateOrganizationUseCase;
import com.orbit.organization.application.port.in.command.RequestParticipationUseCase;
import com.orbit.organization.application.port.in.command.dto.ApproveParticipationRequestsCommand;
import com.orbit.organization.application.port.in.command.dto.ApproveParticipationRequestsCommand.Approval;
import com.orbit.organization.application.port.in.command.dto.ChangeParticipantRoleCommand;
import com.orbit.organization.application.port.in.command.dto.CreateOrganizationCommand;
import com.orbit.organization.application.port.in.command.dto.CreatedOrganizationInfo;
import com.orbit.organization.application.port.in.command.dto.ParticipantRoleChangeInfo;
import com.orbit.organization.application.port.in.command.dto.RequestParticipationCommand;
import com.orbit.organization.domain.Industry;
import com.orbit.organization.domain.ParticipationType;
import com.orbit.organization.domain.RequestChannel;
import com.orbit.support.IntegrationTestSupport;

/**
 * 다른 모듈은 역할 변경 이벤트를 {@link ApplicationModuleListener}(커밋 후, 별도 스레드·트랜잭션)로 소비한다. 같은 방식의 테스트 수신기로 커밋된 역할 변경의
 * 이벤트만 전달되는지 실제 트랜잭션 경계에서 확인한다. 직원을 기사로 바꾸는 쪽은 schedule 구현 전 임시 구현이 막으므로 기사를 직원으로 바꾼다.
 */
@Import(ParticipantRoleChangedEventIntegrationTest.RecorderConfiguration.class)
@DisplayName("역할 변경 이벤트 전달")
class ParticipantRoleChangedEventIntegrationTest extends IntegrationTestSupport {

    private static final long OWNER = 94_001L;

    @Autowired
    private CreateOrganizationUseCase createOrganizationUseCase;

    @Autowired
    private RequestParticipationUseCase requestParticipationUseCase;

    @Autowired
    private ApproveParticipationRequestsUseCase approveUseCase;

    @Autowired
    private ChangeParticipantRoleUseCase changeRoleUseCase;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EventRecorder recorder;

    private CreatedOrganizationInfo organization;

    @BeforeEach
    void setUp() {
        recorder.events().clear();
        organization = createOrganizationUseCase.create(
                new CreateOrganizationCommand(OWNER, "역할 이벤트 발주사 " + System.nanoTime(), Industry.HVAC));
    }

    @Test
    @DisplayName("커밋된 역할 변경은 이전·새 역할과 새 직원 소속을 담은 이벤트로 다른 스레드에 전달된다")
    void deliversRoleChangedEventAfterCommit() throws Exception {
        long technician = 94_101L;
        long technicianId = joinAsTechnician(technician);

        ParticipantRoleChangeInfo info = changeRoleUseCase.changeRole(new ChangeParticipantRoleCommand(
                OWNER, organization.organizationId(), ParticipationType.TECHNICIAN, technicianId, null));

        Object event = recorder.events().poll(5, TimeUnit.SECONDS);
        assertThat(event).isInstanceOfSatisfying(ParticipantRoleChanged.class, changed -> {
            assertThat(changed.organizationId()).isEqualTo(organization.organizationId());
            assertThat(changed.accountId()).isEqualTo(technician);
            assertThat(changed.previousRole()).isEqualTo(ParticipantRoleChanged.Role.TECHNICIAN);
            assertThat(changed.newRole()).isEqualTo(ParticipantRoleChanged.Role.STAFF);
            assertThat(changed.memberId()).isEqualTo(info.memberId());
            assertThat(changed.releasedWorkIds()).isEmpty();
            assertThat(changed.occurredAt()).isNotNull();
        });
        assertThat(recorder.threads().poll(1, TimeUnit.SECONDS))
                .isNotEqualTo(Thread.currentThread().getName());
    }

    @Test
    @DisplayName("롤백된 역할 변경의 이벤트는 전달되지 않고 기사 계약은 활성으로 남는다")
    void dropsEventOfRolledBackRoleChange() throws Exception {
        long technicianId = joinAsTechnician(94_201L);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            changeRoleUseCase.changeRole(new ChangeParticipantRoleCommand(
                    OWNER, organization.organizationId(), ParticipationType.TECHNICIAN, technicianId, null));
            status.setRollbackOnly();
        });

        assertThat(recorder.events().poll(1, TimeUnit.SECONDS)).isNull();
        assertThat(jdbcTemplate.queryForObject(
                        "select status from technician where id = ?", String.class, technicianId))
                .isEqualTo("ACTIVE");
    }

    private long joinAsTechnician(long applicant) {
        long requestId = requestParticipationUseCase
                .request(new RequestParticipationCommand(
                        applicant,
                        organization.companyCode(),
                        ParticipationType.TECHNICIAN,
                        RequestChannel.COMPANY_CODE))
                .requestId();
        approveUseCase.approve(new ApproveParticipationRequestsCommand(
                OWNER, organization.organizationId(), List.of(new Approval(requestId, ParticipationType.TECHNICIAN))));
        recorder.events().clear();
        return jdbcTemplate.queryForObject(
                "select id from technician where company_id = ? and member_id = ?",
                Long.class,
                organization.organizationId(),
                applicant);
    }

    @TestConfiguration
    static class RecorderConfiguration {

        @Bean
        EventRecorder participantRoleChangedEventRecorder() {
            return new EventRecorder();
        }
    }

    /** 리스너 애너테이션 때문에 프록시로 감싸지므로 필드가 아닌 메서드로 꺼낸다. */
    static class EventRecorder {

        private final BlockingQueue<Object> events = new LinkedBlockingQueue<>();
        private final BlockingQueue<String> threads = new LinkedBlockingQueue<>();

        public BlockingQueue<Object> events() {
            return events;
        }

        public BlockingQueue<String> threads() {
            return threads;
        }

        @ApplicationModuleListener
        public void on(ParticipantRoleChanged event) {
            threads.add(Thread.currentThread().getName());
            events.add(event);
        }
    }
}
