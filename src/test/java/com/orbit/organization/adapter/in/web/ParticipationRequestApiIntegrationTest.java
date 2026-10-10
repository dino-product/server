package com.orbit.organization.adapter.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Date;
import java.util.Locale;
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
        return jwt.serialize();
    }

    private record Company(long organizationId, String code) {}
}
