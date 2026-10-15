package com.orbit.organization.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.orbit.support.IntegrationTestSupport;
import com.orbit.support.SignupTestSupport;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 요청자는 실제 Access Token 인증을 거친 auth의 {@code AccountPrincipal}에서 얻는다. 발주사·회사 코드는 발주사 생성·회사 코드 변경 API로 만들고, 승인·역할 변경
 * 유즈케이스가 아직 없어 직원 소속·기사 계약 행은 SQL로 넣는다.
 */
@AutoConfigureMockMvc
@DisplayName("회사 확인·참여 요청 API")
class ParticipationRequestApiIntegrationTest extends IntegrationTestSupport {

    private static final AtomicLong ACCOUNT_IDS = new AtomicLong(3_000);
    private static final Instant JOINED_AT = Instant.parse("2026-10-01T00:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private final SignupTestSupport.SignupOnce signups = new SignupTestSupport.SignupOnce();

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${app.auth.jwt.secret}")
    private String jwtSecret;

    @Value("${app.auth.jwt.issuer}")
    private String jwtIssuer;

    @Test
    @DisplayName("소속이 없는 계정이 소문자·하이픈·공백이 섞인 코드로 확인하면 회사명과 참여 가능 상태를 돌려준다")
    void checksCompanyWithNormalizedInput() throws Exception {
        Company company = createCompany("확인 발주사");

        check(ACCOUNT_IDS.incrementAndGet(), sloppy(company.code()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.result.organizationId").value(company.organizationId()))
                .andExpect(jsonPath("$.result.organizationName").value("확인 발주사"))
                .andExpect(jsonPath("$.result.availability").value("AVAILABLE"))
                .andExpect(jsonPath("$.result.pendingRequestId").doesNotExist());
    }

    @Test
    @DisplayName("총관리자가 자기 회사를 확인하면 이미 참여 중이다")
    void reportsOwnerAsAlreadyMember() throws Exception {
        long owner = ACCOUNT_IDS.incrementAndGet();
        Company company = createCompany(owner, "내 발주사");

        check(owner, company.code())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.availability").value("ALREADY_MEMBER"));
    }

    @Test
    @DisplayName("비활성 기사 계약만 있는 회사는 비활성 소속 상태로 알린다")
    void reportsDeactivatedMember() throws Exception {
        long accountId = ACCOUNT_IDS.incrementAndGet();
        Company company = createCompany("기사 비활성 발주사");
        insertTechnician(company.organizationId(), accountId, "DEACTIVATED");

        check(accountId, company.code())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.availability").value("DEACTIVATED_MEMBER"));
    }

    @Test
    @DisplayName("회사 코드를 바꾸면 이전 코드는 이전 코드로, 없는 코드는 회사 확인 실패로 구분해 거부한다")
    void distinguishesRetiredCodeFromUnknownCode() throws Exception {
        long owner = ACCOUNT_IDS.incrementAndGet();
        Company company = createCompany(owner, "코드 변경 발주사");
        String newCode = changeCode(owner, company.organizationId());
        long requester = ACCOUNT_IDS.incrementAndGet();

        check(requester, sloppy(company.code()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORGANIZATION-012"));
        check(requester, newCode)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.organizationId").value(company.organizationId()));
        check(requester, "UUUUUU")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORGANIZATION-011"));
        check(requester, "7K2M")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORGANIZATION-011"));
    }

    @Test
    @DisplayName("회사 확인과 참여 요청에서 합쳐 열 번 없는 코드를 넣은 계정은 맞는 코드도 429로 거부하고 다른 계정은 그대로 확인한다")
    void blocksAccountAfterRepeatedUnknownCodes() throws Exception {
        Company company = createCompany("대입 방어 발주사");
        long attacker = ACCOUNT_IDS.incrementAndGet();
        for (int attempt = 0; attempt < 5; attempt++) {
            check(attacker, "UUUUUU").andExpect(status().isNotFound());
            request(attacker, "7K2M", "STAFF").andExpect(status().isNotFound());
        }

        check(attacker, company.code())
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("ORGANIZATION-018"));
        request(attacker, company.code(), "STAFF")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("ORGANIZATION-018"));
        check(ACCOUNT_IDS.incrementAndGet(), company.code()).andExpect(status().isOk());
    }

    @Test
    @DisplayName("한 계정이 없는 코드 확인을 한꺼번에 몰아 보내도 코드를 찾아보는 것은 열 번뿐이고 나머지는 429로 거부한다")
    void limitsConcurrentUnknownCodeChecksToMaxFailures() throws Exception {
        long attacker = ACCOUNT_IDS.incrementAndGet();
        int attempts = 30;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        try {
            List<Future<Integer>> statuses = new ArrayList<>();
            for (int attempt = 0; attempt < attempts; attempt++) {
                statuses.add(executor.submit(() -> {
                    start.await();
                    return check(attacker, "UUUUUU").andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            List<Integer> results = new ArrayList<>();
            for (Future<Integer> status : statuses) {
                results.add(status.get());
            }

            assertThat(results).filteredOn(status -> status == 404).hasSize(10);
            assertThat(results).filteredOn(status -> status == 429).hasSize(attempts - 10);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("회사 코드 없이 확인하면 400, 인증하지 않으면 401로 거부한다")
    void rejectsMissingCodeAndUnauthenticatedCheck() throws Exception {
        mockMvc.perform(get("/api/v1/organizations/lookup")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(ACCOUNT_IDS.incrementAndGet())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-400"));
        mockMvc.perform(get("/api/v1/organizations/lookup").param("companyCode", "7K2M9X"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON-401"));
    }

    @Test
    @DisplayName("희망 유형으로 승인 대기 요청을 만들고, 회사 확인은 그 요청을 승인 대기 중으로 알린다")
    void createsPendingRequestShownByCheck() throws Exception {
        long requester = ACCOUNT_IDS.incrementAndGet();
        Company company = createCompany("요청 발주사");

        long requestId = result(request(requester, sloppy(company.code()), "TECHNICIAN")
                        .andExpect(jsonPath("$.result.organizationId").value(company.organizationId()))
                        .andExpect(jsonPath("$.result.organizationName").value("요청 발주사"))
                        .andExpect(jsonPath("$.result.desiredType").value("TECHNICIAN"))
                        .andExpect(jsonPath("$.result.status").value("PENDING"))
                        .andExpect(jsonPath("$.result.requestedAt").exists()))
                .path("requestId")
                .asLong();

        check(requester, company.code())
                .andExpect(jsonPath("$.result.availability").value("PENDING_REQUEST"))
                .andExpect(jsonPath("$.result.pendingRequestId").value(requestId));
        assertThat(jdbcTemplate.queryForObject(
                        "select desired_type from participation_requests where id = ?", String.class, requestId))
                .isEqualTo("TECHNICIAN");
    }

    @Test
    @DisplayName("같은 발주사에 승인 대기 요청이 있으면 새로 만들지 않고, 다른 발주사에는 동시에 요청할 수 있다")
    void rejectsDuplicatePendingButAllowsOtherOrganization() throws Exception {
        long requester = ACCOUNT_IDS.incrementAndGet();
        Company first = createCompany("첫 발주사");
        Company second = createCompany("둘째 발주사");
        result(request(requester, first.code(), "STAFF"));

        request(requester, first.code(), "TECHNICIAN")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORGANIZATION-014"));
        result(request(requester, second.code(), "TECHNICIAN"));

        assertThat(jdbcTemplate.queryForList(
                        "select company_id from participation_requests where member_id = ? and status = 'PENDING'",
                        Long.class,
                        requester))
                .containsExactlyInAnyOrder(first.organizationId(), second.organizationId());
    }

    @Test
    @DisplayName("이미 참여 중이거나 비활성 소속이 있는 발주사에는 요청을 만들지 않는다")
    void rejectsMemberAndDeactivatedMember() throws Exception {
        long owner = ACCOUNT_IDS.incrementAndGet();
        Company company = createCompany(owner, "소속 발주사");
        long deactivated = ACCOUNT_IDS.incrementAndGet();
        insertTechnician(company.organizationId(), deactivated, "DEACTIVATED");

        request(owner, company.code(), "STAFF")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORGANIZATION-013"));
        request(deactivated, company.code(), "TECHNICIAN")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORGANIZATION-015"));
        assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from participation_requests where company_id = ?",
                        Long.class,
                        company.organizationId()))
                .isZero();
    }

    @Test
    @DisplayName("코드를 바꾸면 이전 코드로는 요청할 수 없고, 바꾸기 전에 접수된 승인 대기 요청은 그대로 남는다")
    void keepsPendingRequestAcrossCodeChangeAndRejectsRetiredCode() throws Exception {
        long owner = ACCOUNT_IDS.incrementAndGet();
        Company company = createCompany(owner, "코드 변경 요청 발주사");
        long waiting = ACCOUNT_IDS.incrementAndGet();
        long requestId = result(request(waiting, company.code(), "STAFF"))
                .path("requestId")
                .asLong();
        String newCode = changeCode(owner, company.organizationId());

        request(ACCOUNT_IDS.incrementAndGet(), company.code(), "STAFF")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORGANIZATION-012"));
        check(waiting, newCode)
                .andExpect(jsonPath("$.result.availability").value("PENDING_REQUEST"))
                .andExpect(jsonPath("$.result.pendingRequestId").value(requestId));
        request(waiting, newCode, "STAFF")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORGANIZATION-014"));
        result(request(ACCOUNT_IDS.incrementAndGet(), newCode, "TECHNICIAN"));
    }

    @Test
    @DisplayName("희망 유형이나 회사 코드가 없으면 400, 인증하지 않으면 401로 거부한다")
    void rejectsInvalidBodyAndUnauthenticatedRequest() throws Exception {
        long requester = ACCOUNT_IDS.incrementAndGet();
        mockMvc.perform(post("/api/v1/participation-requests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(requester))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyCode\":\"7K2M9X\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-400"));
        mockMvc.perform(post("/api/v1/participation-requests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(requester))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"desiredType\":\"OWNER\",\"companyCode\":\"7K2M9X\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-400"));
        mockMvc.perform(post("/api/v1/participation-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyCode\":\"7K2M9X\",\"desiredType\":\"STAFF\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON-401"));
    }

    @Test
    @DisplayName("승인 대기 요청을 취소하면 취소 상태가 되고 현재 코드로 새 요청을 다시 보낼 수 있다")
    void cancelsPendingRequestAndAllowsNewRequest() throws Exception {
        long requester = ACCOUNT_IDS.incrementAndGet();
        Company company = createCompany("취소 발주사");
        long requestId = result(request(requester, company.code(), "STAFF"))
                .path("requestId")
                .asLong();

        cancel(requester, requestId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.requestId").value(requestId))
                .andExpect(jsonPath("$.result.organizationName").value("취소 발주사"))
                .andExpect(jsonPath("$.result.status").value("CANCELED"));
        check(requester, company.code())
                .andExpect(jsonPath("$.result.availability").value("AVAILABLE"));
        long newRequestId = result(request(requester, company.code(), "TECHNICIAN"))
                .path("requestId")
                .asLong();

        assertThat(newRequestId).isNotEqualTo(requestId);
        assertThat(jdbcTemplate.queryForList(
                        "select status from participation_requests where member_id = ? order by id",
                        String.class,
                        requester))
                .containsExactly("CANCELED", "PENDING");
        assertThat(jdbcTemplate.queryForObject(
                        "select decided_at from participation_requests where id = ?", Timestamp.class, requestId))
                .isNull();
    }

    @Test
    @DisplayName("이미 취소된 요청은 다시 취소할 수 없다")
    void rejectsCancelOfCanceledRequest() throws Exception {
        long requester = ACCOUNT_IDS.incrementAndGet();
        long requestId = result(request(requester, createCompany("재취소 발주사").code(), "STAFF"))
                .path("requestId")
                .asLong();
        cancel(requester, requestId).andExpect(status().isOk());

        cancel(requester, requestId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORGANIZATION-017"));
    }

    @Test
    @DisplayName("다른 계정의 요청과 없는 요청은 403으로 거부하고 요청은 그대로 남는다")
    void rejectsCancelOfOthersOrMissingRequest() throws Exception {
        long requester = ACCOUNT_IDS.incrementAndGet();
        long requestId = result(request(requester, createCompany("남의 요청 발주사").code(), "STAFF"))
                .path("requestId")
                .asLong();
        long other = ACCOUNT_IDS.incrementAndGet();

        cancel(other, requestId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORGANIZATION-016"));
        cancel(other, Long.MAX_VALUE)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORGANIZATION-016"));
        cancel(requester, 0L)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-400"));
        mockMvc.perform(post("/api/v1/participation-requests/{requestId}/cancel", requestId))
                .andExpect(status().isUnauthorized());
        assertThat(jdbcTemplate.queryForObject(
                        "select status from participation_requests where id = ?", String.class, requestId))
                .isEqualTo("PENDING");
    }

    @Test
    @DisplayName("신청 현황은 보낸 요청을 취소·재요청 포함 모두 요청 일시순으로 보이고 다른 계정의 요청은 보이지 않는다")
    void listsAllOwnRequestsInRequestOrder() throws Exception {
        long requester = ACCOUNT_IDS.incrementAndGet();
        Company first = createCompany("현황 첫 발주사");
        Company second = createCompany("현황 둘째 발주사");
        long canceled = result(request(requester, first.code(), "STAFF"))
                .path("requestId")
                .asLong();
        cancel(requester, canceled).andExpect(status().isOk());
        long pendingOther = result(request(requester, second.code(), "TECHNICIAN"))
                .path("requestId")
                .asLong();
        long renewed = result(request(requester, first.code(), "TECHNICIAN"))
                .path("requestId")
                .asLong();
        result(request(ACCOUNT_IDS.incrementAndGet(), first.code(), "STAFF"));

        listMine(requester)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.requests.length()").value(3))
                .andExpect(jsonPath("$.result.requests[0].requestId").value(canceled))
                .andExpect(jsonPath("$.result.requests[0].organizationName").value("현황 첫 발주사"))
                .andExpect(jsonPath("$.result.requests[0].desiredType").value("STAFF"))
                .andExpect(jsonPath("$.result.requests[0].status").value("CANCELED"))
                .andExpect(jsonPath("$.result.requests[0].decidedAt").doesNotExist())
                .andExpect(jsonPath("$.result.requests[1].requestId").value(pendingOther))
                .andExpect(jsonPath("$.result.requests[1].organizationName").value("현황 둘째 발주사"))
                .andExpect(jsonPath("$.result.requests[1].status").value("PENDING"))
                .andExpect(jsonPath("$.result.requests[2].requestId").value(renewed))
                .andExpect(jsonPath("$.result.requests[2].status").value("PENDING"))
                .andExpect(jsonPath("$.result.requests[2].requestedAt").exists());
    }

    @Test
    @DisplayName("요청이 없으면 빈 신청 현황이고, 인증하지 않으면 401로 거부한다")
    void returnsEmptyListAndRejectsUnauthenticatedList() throws Exception {
        listMine(ACCOUNT_IDS.incrementAndGet())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.requests").isEmpty());
        mockMvc.perform(get("/api/v1/participation-requests"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON-401"));
    }

    private ResultActions listMine(long accountId) throws Exception {
        return mockMvc.perform(get("/api/v1/participation-requests")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(accountId)));
    }

    private ResultActions cancel(long accountId, long requestId) throws Exception {
        return mockMvc.perform(post("/api/v1/participation-requests/{requestId}/cancel", requestId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(accountId)));
    }

    private ResultActions request(long accountId, String companyCode, String desiredType) throws Exception {
        return mockMvc.perform(post("/api/v1/participation-requests")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(accountId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        Map.of("companyCode", companyCode, "desiredType", desiredType))));
    }

    private ResultActions check(long accountId, String companyCode) throws Exception {
        return mockMvc.perform(get("/api/v1/organizations/lookup")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(accountId))
                .param("companyCode", companyCode));
    }

    private Company createCompany(String name) throws Exception {
        return createCompany(ACCOUNT_IDS.incrementAndGet(), name);
    }

    private Company createCompany(long owner, String name) throws Exception {
        JsonNode result = result(mockMvc.perform(post("/api/v1/organizations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", name, "industry", "HVAC")))));
        return new Company(
                result.path("organizationId").asLong(),
                result.path("companyCode").asText());
    }

    private String changeCode(long owner, long organizationId) throws Exception {
        return result(mockMvc.perform(post("/api/v1/organizations/{organizationId}/company-code", organizationId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(owner))))
                .path("companyCode")
                .asText();
    }

    private JsonNode result(ResultActions actions) throws Exception {
        String body =
                actions.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).path("result");
    }

    /** 사용자가 소문자·하이픈·공백을 섞어 입력한 모양. */
    private static String sloppy(String code) {
        return " " + code.substring(0, 3).toLowerCase(Locale.ROOT) + "-" + code.substring(3) + " ";
    }

    private void insertTechnician(long organizationId, long accountId, String status) {
        jdbcTemplate.update(
                """
                insert into technician (company_id, member_id, status, status_changed_at, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?)
                """,
                organizationId,
                accountId,
                status,
                Timestamp.from(JOINED_AT),
                Timestamp.from(JOINED_AT),
                Timestamp.from(JOINED_AT));
    }

    private String accessToken(long accountId) throws Exception {
        Instant issuedAt = Instant.now();
        SignedJWT jwt = new SignedJWT(
                new JWSHeader(JWSAlgorithm.HS256),
                new JWTClaimsSet.Builder()
                        .issuer(jwtIssuer)
                        .subject(Long.toString(accountId))
                        .jwtID(UUID.randomUUID().toString())
                        .issueTime(Date.from(issuedAt))
                        .expirationTime(Date.from(issuedAt.plusSeconds(600)))
                        .claim("token_use", "access")
                        .build());
        jwt.sign(new MACSigner(new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        String accessToken = jwt.serialize();
        signups.ensure(mockMvc, objectMapper, accountId, accessToken);
        return accessToken;
    }

    private record Company(long organizationId, String code) {}
}
