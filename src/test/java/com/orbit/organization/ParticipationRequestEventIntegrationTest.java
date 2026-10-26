package com.orbit.organization;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
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
import com.orbit.organization.application.port.in.command.CreateOrganizationUseCase;
import com.orbit.organization.application.port.in.command.RejectParticipationRequestsUseCase;
import com.orbit.organization.application.port.in.command.RequestParticipationUseCase;
import com.orbit.organization.application.port.in.command.dto.ApproveParticipationRequestsCommand;
import com.orbit.organization.application.port.in.command.dto.ApproveParticipationRequestsCommand.Approval;
import com.orbit.organization.application.port.in.command.dto.CreateOrganizationCommand;
import com.orbit.organization.application.port.in.command.dto.CreatedOrganizationInfo;
import com.orbit.organization.application.port.in.command.dto.RejectParticipationRequestsCommand;
import com.orbit.organization.application.port.in.command.dto.RequestParticipationCommand;
import com.orbit.organization.domain.Industry;
import com.orbit.organization.domain.ParticipationType;
import com.orbit.organization.domain.RequestChannel;
import com.orbit.support.IntegrationTestSupport;

/**
 * 다른 모듈은 승인·거절 이벤트를 {@link ApplicationModuleListener}(커밋 후, 별도 스레드·트랜잭션)로 소비한다. 같은 방식의 테스트 수신기로 커밋된 처리의 이벤트만
 * 전달되는지 실제 트랜잭션 경계에서 확인한다.
 */
@Import(ParticipationRequestEventIntegrationTest.RecorderConfiguration.class)
@DisplayName("참여 요청 승인·거절 이벤트 전달")
class ParticipationRequestEventIntegrationTest extends IntegrationTestSupport {

    private static final long OWNER = 93_001L;

    @Autowired
    private CreateOrganizationUseCase createOrganizationUseCase;

    @Autowired
    private RequestParticipationUseCase requestParticipationUseCase;

    @Autowired
    private ApproveParticipationRequestsUseCase approveUseCase;

    @Autowired
    private RejectParticipationRequestsUseCase rejectUseCase;

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
                new CreateOrganizationCommand(OWNER, "이벤트 발주사 " + System.nanoTime(), Industry.HVAC));
    }

    @Test
    @DisplayName("커밋된 승인은 희망·확정 유형을 담은 승인 이벤트로 다른 스레드에 전달된다")
    void deliversApprovedEventAfterCommit() throws Exception {
        long applicant = 93_101L;
        long requestId = request(applicant, ParticipationType.TECHNICIAN);

        approveUseCase.approve(new ApproveParticipationRequestsCommand(
                OWNER, organization.organizationId(), List.of(new Approval(requestId, ParticipationType.STAFF))));

        Object event = recorder.events().poll(5, TimeUnit.SECONDS);
        assertThat(event).isInstanceOfSatisfying(ParticipationRequestApproved.class, approved -> {
            assertThat(approved.requestId()).isEqualTo(requestId);
            assertThat(approved.organizationId()).isEqualTo(organization.organizationId());
            assertThat(approved.accountId()).isEqualTo(applicant);
            assertThat(approved.desiredType()).isEqualTo(ParticipationRequestApproved.Type.TECHNICIAN);
            assertThat(approved.confirmedType()).isEqualTo(ParticipationRequestApproved.Type.STAFF);
            assertThat(approved.occurredAt()).isNotNull();
        });
        assertThat(recorder.threads().poll(1, TimeUnit.SECONDS))
                .isNotEqualTo(Thread.currentThread().getName());
    }

    @Test
    @DisplayName("커밋된 거절은 사유를 담은 거절 이벤트로 전달된다")
    void deliversRejectedEventAfterCommit() throws Exception {
        long applicant = 93_201L;
        long requestId = request(applicant, ParticipationType.STAFF);

        rejectUseCase.reject(new RejectParticipationRequestsCommand(
                OWNER, organization.organizationId(), List.of(requestId), "인원 마감"));

        assertThat(recorder.events().poll(5, TimeUnit.SECONDS))
                .isEqualTo(new ParticipationRequestRejected(
                        requestId, organization.organizationId(), applicant, "인원 마감", decidedAt(requestId)));
    }

    @Test
    @DisplayName("롤백된 승인의 이벤트는 전달되지 않고 요청은 승인 대기로 남는다")
    void dropsEventOfRolledBackApproval() throws Exception {
        long requestId = request(93_301L, ParticipationType.STAFF);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            approveUseCase.approve(new ApproveParticipationRequestsCommand(
                    OWNER, organization.organizationId(), List.of(new Approval(requestId, ParticipationType.STAFF))));
            status.setRollbackOnly();
        });

        assertThat(recorder.events().poll(1, TimeUnit.SECONDS)).isNull();
        assertThat(jdbcTemplate.queryForObject(
                        "select status from participation_requests where id = ?", String.class, requestId))
                .isEqualTo("PENDING");
    }

    private long request(long applicant, ParticipationType desiredType) {
        return requestParticipationUseCase
                .request(new RequestParticipationCommand(
                        applicant, organization.companyCode(), desiredType, RequestChannel.COMPANY_CODE))
                .requestId();
    }

    private Instant decidedAt(long requestId) {
        return jdbcTemplate
                .queryForObject(
                        "select decided_at from participation_requests where id = ?", Timestamp.class, requestId)
                .toInstant();
    }

    @TestConfiguration
    static class RecorderConfiguration {

        @Bean
        EventRecorder participationRequestEventRecorder() {
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
        public void on(ParticipationRequestApproved event) {
            record(event);
        }

        @ApplicationModuleListener
        public void on(ParticipationRequestRejected event) {
            record(event);
        }

        private void record(Object event) {
            threads.add(Thread.currentThread().getName());
            events.add(event);
        }
    }
}
