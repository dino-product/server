package com.orbit.organization.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
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

/** 요청자는 실제 Access Token 인증을 거친 auth의 {@code AccountPrincipal}에서 얻는다. 계정마다 다른 {@code sub}로 토큰을 만든다. */
@AutoConfigureMockMvc
@DisplayName("발주사 생성 API")
class OrganizationApiIntegrationTest extends IntegrationTestSupport {

    private static final AtomicLong ACCOUNT_IDS = new AtomicLong(1_000);

    private long requesterAccountId;

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
    @DisplayName("발주사를 만들면 발주사·생성자의 총관리자 직원 소속·회사 코드가 함께 저장된다")
    void createsOrganizationWithOwnerMembershipAndCompanyCode() throws Exception {
        long accountId = requester();

        String body = create(Map.of("name", "오르빗 설비", "industry", "FACILITY_MANAGEMENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.result.organizationId").isNumber())
                .andExpect(jsonPath("$.result.companyCode").value(matchesPattern("[0-9ABCDEFGHJKMNPQRSTVWXYZ]{6}")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long organizationId = objectMapper
                .readTree(body)
                .path("result")
                .path("organizationId")
                .asLong();
        String companyCode =
                objectMapper.readTree(body).path("result").path("companyCode").asText();

        assertThat(jdbcTemplate.queryForMap(
                        "select name, industry_code, code from companies where id = ?", organizationId))
                .containsEntry("name", "오르빗 설비")
                .containsEntry("industry_code", "FACILITY_MANAGEMENT")
                .containsEntry("code", companyCode);
        assertThat(jdbcTemplate.queryForList(
                        "select member_id, is_owner, status from company_memberships where company_id = ?",
                        organizationId))
                .singleElement()
                .satisfies(row -> assertThat(row)
                        .containsEntry("member_id", accountId)
                        .containsEntry("is_owner", true)
                        .containsEntry("status", "ACTIVE"));
    }

    @Test
    @DisplayName("이미 다른 발주사에 소속된 계정도 새 발주사를 만들 수 있다")
    void allowsAccountAlreadyInAnotherOrganization() throws Exception {
        requester();
        create(Map.of("name", "첫 번째 발주사", "industry", "HVAC")).andExpect(status().isOk());

        create(Map.of("name", "두 번째 발주사", "industry", "HVAC")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("발주사명이 2~30자를 벗어나면 400으로 거부한다")
    void rejectsNameOutsideAllowedLength() throws Exception {
        requester();

        create(Map.of("name", "가", "industry", "HVAC"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ORGANIZATION-001"));
        create(Map.of("name", "가".repeat(31), "industry", "HVAC"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ORGANIZATION-001"));
    }

    @Test
    @DisplayName("발주사명의 앞뒤 공백은 지우고 저장한다")
    void storesNameWithoutSurroundingWhitespace() throws Exception {
        requester();

        String body = create(Map.of("name", "  오르빗 설비  ", "industry", "HVAC"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long organizationId = objectMapper
                .readTree(body)
                .path("result")
                .path("organizationId")
                .asLong();

        assertThat(jdbcTemplate.queryForObject("select name from companies where id = ?", String.class, organizationId))
                .isEqualTo("오르빗 설비");
    }

    @Test
    @DisplayName("이모지가 들어간 발주사명은 400으로 거부한다")
    void rejectsNameContainingEmoji() throws Exception {
        requester();

        create(Map.of("name", "오르빗 설비\uD83D\uDE00", "industry", "HVAC"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ORGANIZATION-001"));
    }

    @Test
    @DisplayName("업종이 없으면 400으로 거부한다")
    void rejectsMissingIndustry() throws Exception {
        requester();

        create(Map.of("name", "오르빗 설비"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ORGANIZATION-001"));
    }

    @Test
    @DisplayName("6종에 없는 업종은 400으로 거부한다")
    void rejectsUnknownIndustry() throws Exception {
        requester();

        create(Map.of("name", "오르빗 설비", "industry", "AGRICULTURE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-400"));
    }

    @Test
    @DisplayName("인증하지 않은 요청은 401로 거부한다")
    void rejectsUnauthenticatedRequest() throws Exception {
        mockMvc.perform(post("/api/v1/organizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "오르빗 설비", "industry", "HVAC"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON-401"));
    }

    private long requester() {
        requesterAccountId = ACCOUNT_IDS.incrementAndGet();
        return requesterAccountId;
    }

    private ResultActions create(Map<String, String> request) throws Exception {
        return mockMvc.perform(post("/api/v1/organizations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    private String accessToken() throws Exception {
        Instant issuedAt = Instant.now();
        SignedJWT jwt = new SignedJWT(
                new JWSHeader(JWSAlgorithm.HS256),
                new JWTClaimsSet.Builder()
                        .issuer(jwtIssuer)
                        .subject(Long.toString(requesterAccountId))
                        .jwtID(UUID.randomUUID().toString())
                        .issueTime(Date.from(issuedAt))
                        .expirationTime(Date.from(issuedAt.plusSeconds(600)))
                        .claim("token_use", "access")
                        .build());
        jwt.sign(new MACSigner(new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        return jwt.serialize();
    }
}
