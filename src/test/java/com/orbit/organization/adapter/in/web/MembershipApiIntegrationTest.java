package com.orbit.organization.adapter.in.web;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Date;
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

import tools.jackson.databind.ObjectMapper;

/**
 * 요청자는 실제 Access Token 인증을 거친 auth의 {@code AccountPrincipal}에서 얻는다. 참여 요청 승인·역할 변경 유즈케이스가 아직 없어 직원·기사 행은 SQL로
 * 넣는다.
 */
@AutoConfigureMockMvc
@DisplayName("내 소속 목록 조회 API")
class MembershipApiIntegrationTest extends IntegrationTestSupport {

    private static final AtomicLong ACCOUNT_IDS = new AtomicLong(2_000);
    private static final AtomicLong COMPANY_CODES = new AtomicLong();
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
    @DisplayName("소속이 없으면 빈 목록을 돌려준다")
    void returnsEmptyListWithoutMembership() throws Exception {
        listMine(ACCOUNT_IDS.incrementAndGet())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.result.memberships").isEmpty());
    }

    @Test
    @DisplayName("총관리자·직원·기사 소속을 발주사명·역할·상태와 함께 돌려주고 다른 계정의 소속은 보이지 않는다")
    void listsOwnMembershipsWithRoleAndStatusOnly() throws Exception {
        long accountId = ACCOUNT_IDS.incrementAndGet();
        long otherAccountId = ACCOUNT_IDS.incrementAndGet();
        long owned = createOrganization(accountId, "총관리 발주사");
        long staffed = insertOrganization("직원 발주사");
        insertMembership(staffed, accountId, "DEACTIVATED");
        long contracted = insertOrganization("기사 발주사");
        insertTechnician(contracted, accountId, "ACTIVE");
        createOrganization(otherAccountId, "남의 발주사");
        insertTechnician(owned, otherAccountId, "ACTIVE");

        listMine(accountId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.memberships.length()").value(3))
                .andExpect(jsonPath("$.result.memberships[*].organizationId")
                        .value(containsInAnyOrder((int) owned, (int) staffed, (int) contracted)))
                .andExpect(jsonPath("$.result.memberships[?(@.organizationId == " + owned + ")].organizationName")
                        .value("총관리 발주사"))
                .andExpect(jsonPath("$.result.memberships[?(@.organizationId == " + owned + ")].role")
                        .value("OWNER"))
                .andExpect(jsonPath("$.result.memberships[?(@.organizationId == " + owned + ")].status")
                        .value("ACTIVE"))
                .andExpect(jsonPath("$.result.memberships[?(@.organizationId == " + staffed + ")].role")
                        .value("STAFF"))
                .andExpect(jsonPath("$.result.memberships[?(@.organizationId == " + staffed + ")].status")
                        .value("DEACTIVATED"))
                .andExpect(jsonPath("$.result.memberships[?(@.organizationId == " + contracted + ")].role")
                        .value("TECHNICIAN"))
                .andExpect(jsonPath("$.result.memberships[?(@.organizationId == " + contracted + ")].status")
                        .value("ACTIVE"));
    }

    @Test
    @DisplayName("인증하지 않은 요청은 401로 거부한다")
    void rejectsUnauthenticatedRequest() throws Exception {
        mockMvc.perform(get("/api/v1/memberships"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON-401"));
    }

    private ResultActions listMine(long accountId) throws Exception {
        return mockMvc.perform(
                get("/api/v1/memberships").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(accountId)));
    }

    private long createOrganization(long accountId, String name) throws Exception {
        String body = mockMvc.perform(post("/api/v1/organizations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(accountId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", name, "industry", "HVAC"))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).path("result").path("organizationId").asLong();
    }

    /** 회사 코드 발급 알파벳에 없는 U로 시작해 발급된 코드와 겹치지 않게 한다. */
    private long insertOrganization(String name) {
        return jdbcTemplate.queryForObject(
                """
                insert into companies (code, name, industry_code, created_at, updated_at)
                values (?, ?, 'HVAC', ?, ?)
                returning id
                """,
                Long.class,
                "U%05d".formatted(COMPANY_CODES.incrementAndGet()),
                name,
                Timestamp.from(JOINED_AT),
                Timestamp.from(JOINED_AT));
    }

    private void insertMembership(long organizationId, long accountId, String status) {
        jdbcTemplate.update(
                """
                insert into company_memberships
                    (company_id, member_id, is_owner, status, joined_at, status_changed_at, created_at, updated_at)
                values (?, ?, false, ?, ?, ?, ?, ?)
                """,
                organizationId,
                accountId,
                status,
                Timestamp.from(JOINED_AT),
                Timestamp.from(JOINED_AT),
                Timestamp.from(JOINED_AT),
                Timestamp.from(JOINED_AT));
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
        return jwt.serialize();
    }
}
