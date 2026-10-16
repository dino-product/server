package com.orbit.organization.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 총관리자의 승인 대기·승인·거절 API. 요청자는 실제 Access Token 인증을 거친 auth의 {@code AccountPrincipal}에서 얻는다. 발주사·참여 요청은 API로 만들고,
 * 총관리자가 아닌 직원 소속과 기사 계약은 역할 지정 유즈케이스가 없어 SQL로 넣는다.
 */
@AutoConfigureMockMvc
@DisplayName("참여 요청 승인 대기·승인·거절 API")
class OrganizationParticipationRequestApiIntegrationTest extends IntegrationTestSupport {

    private static final AtomicLong ACCOUNT_IDS = new AtomicLong(6_000);
    private static final Instant JOINED_AT = Instant.parse("2026-10-01T00:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${app.auth.jwt.secret}")
    private String jwtSecret;

    @Value("${app.auth.jwt.issuer}")
    private String jwtIssuer;

    @Test
    @DisplayName("총관리자는 그 발주사의 승인 대기 요청만 오래된 순으로 건수와 함께 받는다")
    void ownerListsPendingRequestsOldestFirst() throws Exception {
        long owner = ACCOUNT_IDS.incrementAndGet();
        Company company = createCompany(owner, "대기 목록 발주사");
        Company otherCompany = createCompany(ACCOUNT_IDS.incrementAndGet(), "다른 발주사");
        long firstApplicant = ACCOUNT_IDS.incrementAndGet();
        long secondApplicant = ACCOUNT_IDS.incrementAndGet();
        long canceledApplicant = ACCOUNT_IDS.incrementAndGet();
        long first = requestParticipation(firstApplicant, company.code(), "TECHNICIAN");
        long second = requestParticipation(secondApplicant, company.code(), "STAFF");
        cancel(canceledApplicant, requestParticipation(canceledApplicant, company.code(), "STAFF"));
        requestParticipation(ACCOUNT_IDS.incrementAndGet(), otherCompany.code(), "STAFF");

        listPending(owner, company.organizationId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.result.count").value(2))
                .andExpect(jsonPath("$.result.requests.length()").value(2))
                .andExpect(jsonPath("$.result.requests[0].requestId").value(first))
                .andExpect(jsonPath("$.result.requests[0].accountId").value(firstApplicant))
                .andExpect(jsonPath("$.result.requests[0].desiredType").value("TECHNICIAN"))
                .andExpect(jsonPath("$.result.requests[0].requestedAt").exists())
                .andExpect(jsonPath("$.result.requests[1].requestId").value(second))
                .andExpect(jsonPath("$.result.requests[1].desiredType").value("STAFF"));
    }

    @Test
    @DisplayName("승인 대기 요청이 없으면 건수 0과 빈 목록이다")
    void listsNothingWithoutPendingRequests() throws Exception {
        long owner = ACCOUNT_IDS.incrementAndGet();
        Company company = createCompany(owner, "빈 대기 발주사");

        listPending(owner, company.organizationId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.count").value(0))
                .andExpect(jsonPath("$.result.requests.length()").value(0));
    }

    @Test
    @DisplayName("직원은 403 ORGANIZATION-003, 기사·다른 발주사 총관리자는 403 ORGANIZATION-002로 승인 대기 목록을 볼 수 없다")
    void refusesNonOwners() throws Exception {
        Company company = createCompany(ACCOUNT_IDS.incrementAndGet(), "권한 발주사");
        long staff = ACCOUNT_IDS.incrementAndGet();
        long technician = ACCOUNT_IDS.incrementAndGet();
        long otherOwner = ACCOUNT_IDS.incrementAndGet();
        insertStaff(company.organizationId(), staff);
        insertTechnician(company.organizationId(), technician);
        createCompany(otherOwner, "남의 발주사");

        listPending(staff, company.organizationId())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORGANIZATION-003"));
        listPending(technician, company.organizationId())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORGANIZATION-002"));
        listPending(otherOwner, company.organizationId())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORGANIZATION-002"));
    }

    @Test
    @DisplayName("희망 유형 그대로 기사로 승인하면 활성 기사 계약이 생겨 신청자 소속 목록에 보이고 승인 대기에서 빠진다")
    void approvesAsDesiredTechnician() throws Exception {
        long owner = ACCOUNT_IDS.incrementAndGet();
        Company company = createCompany(owner, "기사 승인 발주사");
        long applicant = ACCOUNT_IDS.incrementAndGet();
        long requestId = requestParticipation(applicant, company.code(), "TECHNICIAN");

        approve(owner, company.organizationId(), Map.of("requestId", requestId, "confirmedType", "TECHNICIAN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.results.length()").value(1))
                .andExpect(jsonPath("$.result.results[0].requestId").value(requestId))
                .andExpect(jsonPath("$.result.results[0].outcome").value("APPROVED"))
                .andExpect(jsonPath("$.result.results[0].status").value("APPROVED"));

        assertThat(jdbcTemplate.queryForMap(
                        "select status, company_technician_category_id from technician"
                                + " where company_id = ? and member_id = ?",
                        company.organizationId(),
                        applicant))
                .containsEntry("status", "ACTIVE")
                .containsEntry("company_technician_category_id", null);
        assertThat(decidedByMembershipId(requestId)).isEqualTo(ownerMembershipId(company.organizationId(), owner));
        memberships(applicant)
                .andExpect(jsonPath("$.result.memberships[0].organizationId").value(company.organizationId()))
                .andExpect(jsonPath("$.result.memberships[0].role").value("TECHNICIAN"))
                .andExpect(jsonPath("$.result.memberships[0].status").value("ACTIVE"));
        listMine(applicant)
                .andExpect(jsonPath("$.result.requests[0].status").value("APPROVED"))
                .andExpect(jsonPath("$.result.requests[0].confirmedType").value("TECHNICIAN"))
                .andExpect(jsonPath("$.result.requests[0].decidedAt").exists());
        listPending(owner, company.organizationId())
                .andExpect(jsonPath("$.result.count").value(0));
    }

    @Test
    @DisplayName("비활성 소속이 있는 회원이 다시 요청해 승인되면 같은 역할은 그 행을 되살리고, 다른 역할은 이전 참여일로 새 행을 만들어 소속 목록에 한 줄만 보인다")
    void reactivatesDeactivatedMembersOnApproval() throws Exception {
        long owner = ACCOUNT_IDS.incrementAndGet();
        Company company = createCompany(owner, "재참여 승인 발주사");
        long returningStaff = ACCOUNT_IDS.incrementAndGet();
        long staffToTechnician = ACCOUNT_IDS.incrementAndGet();
        insertDeactivatedStaff(company.organizationId(), returningStaff);
        insertDeactivatedStaff(company.organizationId(), staffToTechnician);
        long first = requestParticipation(returningStaff, company.code(), "STAFF");
        long second = requestParticipation(staffToTechnician, company.code(), "STAFF");

        approve(
                        owner,
                        company.organizationId(),
                        Map.of("requestId", first, "confirmedType", "STAFF"),
                        Map.of("requestId", second, "confirmedType", "TECHNICIAN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.results[0].outcome").value("APPROVED"))
                .andExpect(jsonPath("$.result.results[1].outcome").value("APPROVED"));

        assertThat(jdbcTemplate.queryForMap(
                        "select count(*) as rows, min(status) as status, min(joined_at) as joined_at"
                                + " from company_memberships where company_id = ? and member_id = ?",
                        company.organizationId(),
                        returningStaff))
                .containsEntry("rows", 1L)
                .containsEntry("status", "ACTIVE")
                .containsEntry("joined_at", Timestamp.from(JOINED_AT));
        assertThat(jdbcTemplate.queryForMap(
                        "select status, joined_at from technician where company_id = ? and member_id = ?",
                        company.organizationId(),
                        staffToTechnician))
                .containsEntry("status", "ACTIVE")
                .containsEntry("joined_at", Timestamp.from(JOINED_AT));
        memberships(staffToTechnician)
                .andExpect(jsonPath("$.result.memberships.length()").value(1))
                .andExpect(jsonPath("$.result.memberships[0].role").value("TECHNICIAN"))
                .andExpect(jsonPath("$.result.memberships[0].status").value("ACTIVE"));
    }

    @Test
    @DisplayName("기사 희망 요청을 직원으로 바꿔 승인하면 요청은 기사·소속은 총관리자 아닌 직원으로 남는다")
    void approvesTechnicianRequestAsStaff() throws Exception {
        long owner = ACCOUNT_IDS.incrementAndGet();
        Company company = createCompany(owner, "직원 승인 발주사");
        long applicant = ACCOUNT_IDS.incrementAndGet();
        long requestId = requestParticipation(applicant, company.code(), "TECHNICIAN");

        approve(owner, company.organizationId(), Map.of("requestId", requestId, "confirmedType", "STAFF"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.results[0].outcome").value("APPROVED"));

        memberships(applicant)
                .andExpect(jsonPath("$.result.memberships[0].role").value("STAFF"))
                .andExpect(jsonPath("$.result.memberships[0].status").value("ACTIVE"));
        listMine(applicant)
                .andExpect(jsonPath("$.result.requests[0].desiredType").value("TECHNICIAN"))
                .andExpect(jsonPath("$.result.requests[0].confirmedType").value("STAFF"));
        assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from technician where company_id = ? and member_id = ?",
                        Long.class,
                        company.organizationId(),
                        applicant))
                .isZero();
    }

    @Test
    @DisplayName("세 건 중 두 건과 취소된 요청을 함께 승인하면 두 건만 각자의 확정 유형으로 처리하고 취소된 요청은 현재 상태로 알린다")
    void approvesSelectedRowsAndReportsCanceledRequest() throws Exception {
        long owner = ACCOUNT_IDS.incrementAndGet();
        Company company = createCompany(owner, "일괄 승인 발주사");
        Company otherCompany = createCompany(ACCOUNT_IDS.incrementAndGet(), "남의 대기 발주사");
        long first = requestParticipation(ACCOUNT_IDS.incrementAndGet(), company.code(), "STAFF");
        long second = requestParticipation(ACCOUNT_IDS.incrementAndGet(), company.code(), "STAFF");
        long untouched = requestParticipation(ACCOUNT_IDS.incrementAndGet(), company.code(), "TECHNICIAN");
        long canceler = ACCOUNT_IDS.incrementAndGet();
        long canceled = requestParticipation(canceler, company.code(), "STAFF");
        cancel(canceler, canceled);
        long otherOrganizations = requestParticipation(ACCOUNT_IDS.incrementAndGet(), otherCompany.code(), "STAFF");

        approve(
                        owner,
                        company.organizationId(),
                        Map.of("requestId", second, "confirmedType", "TECHNICIAN"),
                        Map.of("requestId", first, "confirmedType", "STAFF"),
                        Map.of("requestId", canceled, "confirmedType", "STAFF"),
                        Map.of("requestId", otherOrganizations, "confirmedType", "STAFF"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.results[0].requestId").value(second))
                .andExpect(jsonPath("$.result.results[0].outcome").value("APPROVED"))
                .andExpect(jsonPath("$.result.results[1].requestId").value(first))
                .andExpect(jsonPath("$.result.results[1].outcome").value("APPROVED"))
                .andExpect(jsonPath("$.result.results[2].outcome").value("NOT_PENDING"))
                .andExpect(jsonPath("$.result.results[2].status").value("CANCELED"))
                .andExpect(jsonPath("$.result.results[3].outcome").value("NOT_FOUND"))
                .andExpect(jsonPath("$.result.results[3].status").doesNotExist());

        listPending(owner, company.organizationId())
                .andExpect(jsonPath("$.result.count").value(1))
                .andExpect(jsonPath("$.result.requests[0].requestId").value(untouched));
        assertThat(requestStatus(otherOrganizations)).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("이미 승인된 요청을 다시 승인·거절하면 바꾸지 않고 현재 상태로 알린다")
    void doesNotReprocessDecidedRequest() throws Exception {
        long owner = ACCOUNT_IDS.incrementAndGet();
        Company company = createCompany(owner, "재처리 발주사");
        long requestId = requestParticipation(ACCOUNT_IDS.incrementAndGet(), company.code(), "STAFF");
        approve(owner, company.organizationId(), Map.of("requestId", requestId, "confirmedType", "STAFF"));

        reject(owner, company.organizationId(), "사유", requestId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.results[0].outcome").value("NOT_PENDING"))
                .andExpect(jsonPath("$.result.results[0].status").value("APPROVED"));
        approve(owner, company.organizationId(), Map.of("requestId", requestId, "confirmedType", "TECHNICIAN"))
                .andExpect(jsonPath("$.result.results[0].outcome").value("NOT_PENDING"));
        assertThat(requestStatus(requestId)).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("사유와 함께 거절하면 신청자 현황에 사유가 보이고, 신청자는 같은 회사에 다시 요청할 수 있다")
    void rejectsWithReasonAndAllowsNewRequest() throws Exception {
        long owner = ACCOUNT_IDS.incrementAndGet();
        Company company = createCompany(owner, "거절 발주사");
        long applicant = ACCOUNT_IDS.incrementAndGet();
        long requestId = requestParticipation(applicant, company.code(), "STAFF");

        reject(owner, company.organizationId(), "  이번 달 채용이 마감되었습니다 ", requestId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.results[0].outcome").value("REJECTED"))
                .andExpect(jsonPath("$.result.results[0].status").value("REJECTED"));

        listMine(applicant)
                .andExpect(jsonPath("$.result.requests[0].status").value("REJECTED"))
                .andExpect(jsonPath("$.result.requests[0].rejectReason").value("이번 달 채용이 마감되었습니다"))
                .andExpect(jsonPath("$.result.requests[0].confirmedType").doesNotExist());
        memberships(applicant)
                .andExpect(jsonPath("$.result.memberships.length()").value(0));
        long again = requestParticipation(applicant, company.code(), "STAFF");
        listPending(owner, company.organizationId())
                .andExpect(jsonPath("$.result.requests[0].requestId").value(again));
    }

    @Test
    @DisplayName("사유 없이 여러 건을 거절할 수 있다")
    void rejectsSeveralWithoutReason() throws Exception {
        long owner = ACCOUNT_IDS.incrementAndGet();
        Company company = createCompany(owner, "사유 없는 거절 발주사");
        long first = requestParticipation(ACCOUNT_IDS.incrementAndGet(), company.code(), "STAFF");
        long second = requestParticipation(ACCOUNT_IDS.incrementAndGet(), company.code(), "TECHNICIAN");

        reject(owner, company.organizationId(), null, first, second)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.results[0].outcome").value("REJECTED"))
                .andExpect(jsonPath("$.result.results[1].outcome").value("REJECTED"));
        assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from participation_requests where id in (?, ?) and reject_reason is null",
                        Long.class,
                        first,
                        second))
                .isEqualTo(2L);
    }

    @Test
    @DisplayName("거절 사유가 255자를 넘으면 400 ORGANIZATION-019로 거부하고 요청은 승인 대기로 남는다")
    void refusesTooLongRejectReason() throws Exception {
        long owner = ACCOUNT_IDS.incrementAndGet();
        Company company = createCompany(owner, "긴 사유 발주사");
        long requestId = requestParticipation(ACCOUNT_IDS.incrementAndGet(), company.code(), "STAFF");

        reject(owner, company.organizationId(), "가".repeat(256), requestId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ORGANIZATION-019"));
        reject(owner, company.organizationId(), "가".repeat(255), requestId)
                .andExpect(jsonPath("$.result.results[0].outcome").value("REJECTED"));
    }

    @Test
    @DisplayName("직원의 승인은 403 ORGANIZATION-003, 기사의 거절은 403 ORGANIZATION-002로 거부하고 요청은 그대로다")
    void refusesDecisionsByNonOwners() throws Exception {
        Company company = createCompany(ACCOUNT_IDS.incrementAndGet(), "권한 없는 처리 발주사");
        long staff = ACCOUNT_IDS.incrementAndGet();
        long technician = ACCOUNT_IDS.incrementAndGet();
        insertStaff(company.organizationId(), staff);
        insertTechnician(company.organizationId(), technician);
        long requestId = requestParticipation(ACCOUNT_IDS.incrementAndGet(), company.code(), "STAFF");

        approve(staff, company.organizationId(), Map.of("requestId", requestId, "confirmedType", "STAFF"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORGANIZATION-003"));
        reject(technician, company.organizationId(), null, requestId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORGANIZATION-002"));
        assertThat(requestStatus(requestId)).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("빈 목록·확정 유형 누락·중복 요청은 400 COMMON-400이다")
    void refusesMalformedBatches() throws Exception {
        long owner = ACCOUNT_IDS.incrementAndGet();
        Company company = createCompany(owner, "형식 오류 발주사");
        long requestId = requestParticipation(ACCOUNT_IDS.incrementAndGet(), company.code(), "STAFF");

        approve(owner, company.organizationId())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-400"));
        approve(owner, company.organizationId(), Map.of("requestId", requestId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-400"));
        reject(owner, company.organizationId(), null, requestId, requestId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-400"));
        assertThat(requestStatus(requestId)).isEqualTo("PENDING");
    }

    @SafeVarargs
    private ResultActions approve(long accountId, long organizationId, Map<String, Object>... approvals)
            throws Exception {
        return mockMvc.perform(
                post("/api/v1/organizations/{organizationId}/participation-requests/approvals", organizationId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(accountId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("approvals", List.of(approvals)))));
    }

    private ResultActions reject(long accountId, long organizationId, String reason, Long... requestIds)
            throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("requestIds", List.of(requestIds));
        body.put("reason", reason);
        return mockMvc.perform(
                post("/api/v1/organizations/{organizationId}/participation-requests/rejections", organizationId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(accountId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)));
    }

    private ResultActions listMine(long accountId) throws Exception {
        return mockMvc.perform(get("/api/v1/participation-requests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(accountId)))
                .andExpect(status().isOk());
    }

    private ResultActions memberships(long accountId) throws Exception {
        return mockMvc.perform(get("/api/v1/memberships")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(accountId)))
                .andExpect(status().isOk());
    }

    private String requestStatus(long requestId) {
        return jdbcTemplate.queryForObject(
                "select status from participation_requests where id = ?", String.class, requestId);
    }

    private Long decidedByMembershipId(long requestId) {
        return jdbcTemplate.queryForObject(
                "select decided_by_membership_id from participation_requests where id = ?", Long.class, requestId);
    }

    private Long ownerMembershipId(long organizationId, long owner) {
        return jdbcTemplate.queryForObject(
                "select id from company_memberships where company_id = ? and member_id = ?",
                Long.class,
                organizationId,
                owner);
    }

    private ResultActions listPending(long accountId, long organizationId) throws Exception {
        return mockMvc.perform(get("/api/v1/organizations/{organizationId}/participation-requests", organizationId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(accountId)));
    }

    private long requestParticipation(long accountId, String companyCode, String desiredType) throws Exception {
        return result(mockMvc.perform(post("/api/v1/participation-requests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(accountId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("companyCode", companyCode, "desiredType", desiredType)))))
                .path("requestId")
                .asLong();
    }

    private void cancel(long accountId, long requestId) throws Exception {
        result(mockMvc.perform(post("/api/v1/participation-requests/{requestId}/cancel", requestId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(accountId))));
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

    private JsonNode result(ResultActions actions) throws Exception {
        String body =
                actions.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).path("result");
    }

    private void insertStaff(long organizationId, long accountId) {
        jdbcTemplate.update(
                """
                insert into company_memberships
                    (company_id, member_id, is_owner, status, joined_at, status_changed_at, created_at, updated_at)
                values (?, ?, false, 'ACTIVE', ?, ?, ?, ?)
                """,
                organizationId,
                accountId,
                Timestamp.from(JOINED_AT),
                Timestamp.from(JOINED_AT),
                Timestamp.from(JOINED_AT),
                Timestamp.from(JOINED_AT));
    }

    private void insertDeactivatedStaff(long organizationId, long accountId) {
        jdbcTemplate.update(
                """
                insert into company_memberships
                    (company_id, member_id, is_owner, status, joined_at, status_changed_at, created_at, updated_at)
                values (?, ?, false, 'DEACTIVATED', ?, ?, ?, ?)
                """,
                organizationId,
                accountId,
                Timestamp.from(JOINED_AT),
                Timestamp.from(JOINED_AT.plusSeconds(3600)),
                Timestamp.from(JOINED_AT),
                Timestamp.from(JOINED_AT));
    }

    private void insertTechnician(long organizationId, long accountId) {
        jdbcTemplate.update(
                """
                insert into technician
                    (company_id, member_id, status, status_changed_at, joined_at, updated_at, created_at)
                values (?, ?, 'ACTIVE', ?, ?, ?, current_timestamp)
                """,
                organizationId,
                accountId,
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
        return jwt.serialize();
    }

    private record Company(long organizationId, String code) {}
}
