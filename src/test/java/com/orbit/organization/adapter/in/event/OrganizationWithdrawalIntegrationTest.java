package com.orbit.organization.adapter.in.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.orbit.profile.AccountWithdrawn;
import com.orbit.support.IntegrationTestSupport;
import com.orbit.support.KakaoJwksStub;
import com.orbit.support.SignupTestSupport;
import com.orbit.support.SignupTestSupport.TestAccount;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * profile 탈퇴 API에서 organization의 총관리자 차단 판정과 탈퇴 뒤 소속 비활성화까지 실제 트랜잭션·커밋 뒤 비동기 소비로 확인한다. 탈퇴한 계정은 토큰이
 * 거부되고 카카오 계정이 끊기므로 탈퇴할 계정은 카카오 스텁으로 새로 가입한다. 다른 구성원의 소속은 SQL로 넣는다. 소비가 끝났는지는 처리를 마친 발행이
 * 옮겨지는 이벤트 발행 기록 보관 테이블로 본다.
 */
@DisplayName("organization 탈퇴 연동(PostgreSQL)")
class OrganizationWithdrawalIntegrationTest extends IntegrationTestSupport {

    private static final KakaoJwksStub KAKAO = KakaoJwksStub.start();
    private static final AtomicLong OTHER_ACCOUNTS = new AtomicLong(94_000L);
    private static final AtomicLong COMPANY_CODES = new AtomicLong();
    private static final Duration ASYNC_WAIT = Duration.ofSeconds(10);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @DynamicPropertySource
    static void kakaoJwks(DynamicPropertyRegistry registry) {
        registry.add("app.auth.kakao.jwk-set-uri", KAKAO::jwkSetUri);
    }

    @AfterAll
    static void stopKakaoJwks() {
        KAKAO.close();
    }

    @Test
    @DisplayName("다른 구성원이 있어도 유일한 총관리자는 사전 확인에 SOLE_OWNER로 보이고 탈퇴가 막히며, 다른 총관리자를 지정하면 탈퇴된다")
    void blocksSoleOwnerUntilAnotherOwnerIsDesignated() throws Exception {
        TestAccount owner = signUp();
        long organizationId = createOrganization(owner).path("organizationId").asLong();
        long staffMembership = insertMembership(organizationId, OTHER_ACCOUNTS.incrementAndGet(), false, "ACTIVE");

        checkEligibility(owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.withdrawable").value(false))
                .andExpect(jsonPath("$.result.blocks.length()").value(1))
                .andExpect(jsonPath("$.result.blocks[0].reason").value("SOLE_OWNER"))
                .andExpect(jsonPath("$.result.blocks[0].count").value(1));
        withdraw(owner)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROFILE-006"));
        assertThat(membership(organizationId, owner.accountId())).containsEntry("status", "ACTIVE");

        mockMvc.perform(put(
                                "/api/v1/organizations/{organizationId}/owners/{membershipId}",
                                organizationId,
                                staffMembership)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isNoContent());

        checkEligibility(owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.withdrawable").value(true));
        withdraw(owner).andExpect(status().isNoContent());
        awaitConsumed(owner.accountId(), 1);
        assertThat(membership(organizationId, owner.accountId())).containsEntry("status", "DEACTIVATED");
        assertThat(membership(organizationId, owner.accountId()).get("withdrawn_at"))
                .isNotNull();
        assertThat(jdbcTemplate.queryForMap(
                        "select status, is_owner from company_memberships where id = ?", staffMembership))
                .containsEntry("status", "ACTIVE")
                .containsEntry("is_owner", true);
    }

    @Test
    @DisplayName("탈퇴가 커밋되면 모든 발주사의 직원 소속·기사 계약을 비활성화하고 이미 비활성인 행에도 탈퇴 표시를 남기며 승인 대기 요청을 취소한다")
    void deactivatesEveryMembershipAndCancelsPendingRequestAfterWithdrawal() throws Exception {
        TestAccount member = signUp();
        long staffOrganization = insertOrganization();
        long technicianOrganization = insertOrganization();
        long formerOrganization = insertOrganization();
        insertMembership(staffOrganization, member.accountId(), false, "ACTIVE");
        insertTechnician(technicianOrganization, member.accountId(), "ACTIVE");
        insertTechnician(formerOrganization, member.accountId(), "DEACTIVATED");
        String companyCode = createOrganization(signUp()).path("companyCode").asString();
        long requestId = requestParticipation(member, companyCode);

        checkEligibility(member)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.withdrawable").value(true));
        withdraw(member).andExpect(status().isNoContent());
        awaitConsumed(member.accountId(), 1);

        assertThat(membership(staffOrganization, member.accountId())).containsEntry("status", "DEACTIVATED");
        assertThat(membership(staffOrganization, member.accountId()).get("withdrawn_at"))
                .isNotNull();
        assertThat(technician(technicianOrganization, member.accountId())).containsEntry("status", "DEACTIVATED");
        assertThat(technician(technicianOrganization, member.accountId()).get("withdrawn_at"))
                .isNotNull();
        assertThat(technician(formerOrganization, member.accountId())).containsEntry("status", "DEACTIVATED");
        assertThat(technician(formerOrganization, member.accountId()).get("withdrawn_at"))
                .isNotNull();
        assertThat(jdbcTemplate.queryForObject(
                        "select status from participation_requests where id = ?", String.class, requestId))
                .isEqualTo("CANCELED");
    }

    @Test
    @DisplayName("탈퇴가 롤백되면 소속을 바꾸지 않는다")
    void ignoresRolledBackWithdrawal() {
        long rolledBack = OTHER_ACCOUNTS.incrementAndGet();
        long organizationId = insertOrganization();
        insertMembership(organizationId, rolledBack, false, "ACTIVE");

        publishWithdrawal(rolledBack, false);
        // 롤백된 탈퇴는 소비 대상이 아니므로, 뒤에 커밋한 다른 계정의 탈퇴가 소비된 뒤에도 바뀌지 않았는지 본다.
        long committed = OTHER_ACCOUNTS.incrementAndGet();
        publishWithdrawal(committed, true);
        awaitConsumed(committed, 1);

        assertThat(consumedCount(rolledBack)).isZero();
        assertThat(membership(organizationId, rolledBack))
                .containsEntry("status", "ACTIVE")
                .containsEntry("withdrawn_at", null);
    }

    @Test
    @DisplayName("같은 탈퇴를 다시 받아도 처음 남긴 탈퇴 표시를 그대로 두고 성공한다")
    void redeliveredWithdrawalKeepsFirstResult() {
        long withdrawn = OTHER_ACCOUNTS.incrementAndGet();
        long organizationId = insertOrganization();
        insertMembership(organizationId, withdrawn, false, "ACTIVE");
        publishWithdrawal(withdrawn, true);
        awaitConsumed(withdrawn, 1);
        Map<String, Object> first = membership(organizationId, withdrawn);

        publishWithdrawal(withdrawn, true);
        awaitConsumed(withdrawn, 2);

        assertThat(membership(organizationId, withdrawn)).isEqualTo(first);
        assertThat(first).containsEntry("status", "DEACTIVATED");
    }

    private TestAccount signUp() throws Exception {
        return SignupTestSupport.signUpNewAccount(mockMvc, objectMapper, KAKAO);
    }

    private JsonNode createOrganization(TestAccount founder) throws Exception {
        String body = mockMvc.perform(post("/api/v1/organizations")
                        .header(HttpHeaders.AUTHORIZATION, founder.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "탈퇴 연동 발주사", "industry", "HVAC"))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).path("result");
    }

    private long requestParticipation(TestAccount requester, String companyCode) throws Exception {
        String body = mockMvc.perform(post("/api/v1/participation-requests")
                        .header(HttpHeaders.AUTHORIZATION, requester.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "companyCode", companyCode,
                                "desiredType", "STAFF",
                                "requestChannel", "COMPANY_CODE"))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).path("result").path("requestId").asLong();
    }

    private ResultActions checkEligibility(TestAccount account) throws Exception {
        return mockMvc.perform(get("/api/v1/profiles/{accountId}/withdrawal-eligibility", account.accountId())
                .header(HttpHeaders.AUTHORIZATION, account.bearer()));
    }

    private ResultActions withdraw(TestAccount account) throws Exception {
        return mockMvc.perform(post("/api/v1/profiles/{accountId}/withdrawals", account.accountId())
                .header(HttpHeaders.AUTHORIZATION, account.bearer()));
    }

    /** profile의 탈퇴 트랜잭션처럼 트랜잭션 안에서 탈퇴 이벤트를 발행하고 커밋하거나 롤백한다. */
    private void publishWithdrawal(long accountId, boolean commit) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            eventPublisher.publishEvent(new AccountWithdrawn(accountId, Instant.now()));
            if (!commit) {
                status.setRollbackOnly();
            }
        });
    }

    private void awaitConsumed(long accountId, int count) {
        await().atMost(ASYNC_WAIT).until(() -> consumedCount(accountId) >= count);
    }

    /** organization 리스너가 처리를 마쳐 보관 테이블로 옮겨진 그 계정의 탈퇴 발행 수. */
    private int consumedCount(long accountId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from event_publication_archive"
                        + " where listener_id like 'com.orbit.organization.%' and serialized_event ~ ?",
                Integer.class, "\"accountId\":" + accountId + "[,}]");
    }

    /** 회사 코드 발급 알파벳에 없는 L로 시작해 발급된 코드와 겹치지 않게 한다. */
    private long insertOrganization() {
        Timestamp now = Timestamp.from(Instant.now());
        return jdbcTemplate.queryForObject(
                """
                insert into companies (code, name, industry_code, created_at, updated_at)
                values (?, '탈퇴 연동 발주사', 'HVAC', ?, ?)
                returning id
                """, Long.class, "L%05d".formatted(COMPANY_CODES.incrementAndGet()), now, now);
    }

    private long insertMembership(long organizationId, long accountId, boolean owner, String status) {
        Timestamp now = Timestamp.from(Instant.now());
        return jdbcTemplate.queryForObject(
                """
                insert into company_memberships
                    (company_id, member_id, is_owner, status, joined_at, status_changed_at, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                returning id
                """, Long.class, organizationId, accountId, owner, status, now, now, now, now);
    }

    private void insertTechnician(long organizationId, long accountId, String status) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update("""
                insert into technician
                    (company_id, member_id, status, joined_at, status_changed_at, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?)
                """, organizationId, accountId, status, now, now, now, now);
    }

    private Map<String, Object> membership(long organizationId, long accountId) {
        return jdbcTemplate.queryForMap(
                "select status, withdrawn_at, status_changed_at from company_memberships"
                        + " where company_id = ? and member_id = ?",
                organizationId,
                accountId);
    }

    private Map<String, Object> technician(long organizationId, long accountId) {
        return jdbcTemplate.queryForMap(
                "select status, withdrawn_at from technician where company_id = ? and member_id = ?",
                organizationId,
                accountId);
    }
}
