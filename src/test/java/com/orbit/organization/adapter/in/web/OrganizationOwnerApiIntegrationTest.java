package com.orbit.organization.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

/** 발주사는 API로 만들고(생성자가 첫 총관리자), 다른 직원 소속은 참여 승인 기능이 아직 없어 테이블에 직접 넣는다. */
@AutoConfigureMockMvc
@DisplayName("총관리자 지정·해제 API")
class OrganizationOwnerApiIntegrationTest extends IntegrationTestSupport {

    private static final AtomicLong ACCOUNT_IDS = new AtomicLong(50_000);

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
    @DisplayName("총관리자가 같은 발주사 직원을 지정하면 바로 총관리자가 되고 기존 총관리자도 유지된다")
    void designatesStaffAsAdditionalOwner() throws Exception {
        long owner = account();
        long organizationId = organizationOf(owner);
        long staff = staffMembership(organizationId, account());

        designate(owner, organizationId, staff).andExpect(status().isNoContent());

        assertThat(isOwner(staff)).isTrue();
        assertThat(isOwner(membershipOf(organizationId, owner))).isTrue();
    }

    @Test
    @DisplayName("이미 총관리자인 소속을 다시 지정해도 성공한다")
    void designatingOwnerAgainSucceeds() throws Exception {
        long owner = account();
        long organizationId = organizationOf(owner);

        designate(owner, organizationId, membershipOf(organizationId, owner)).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("총관리자가 아닌 직원이 지정하면 403으로 거부한다")
    void rejectsStaffRequester() throws Exception {
        long owner = account();
        long organizationId = organizationOf(owner);
        long staffAccount = account();
        staffMembership(organizationId, staffAccount);
        long otherStaff = staffMembership(organizationId, account());

        designate(staffAccount, organizationId, otherStaff)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORGANIZATION-003"));
        assertThat(isOwner(otherStaff)).isFalse();
    }

    @Test
    @DisplayName("그 발주사 소속이 아닌 계정이 지정하면 403으로 거부한다")
    void rejectsRequesterOfOtherOrganization() throws Exception {
        long organizationId = organizationOf(account());
        long staff = staffMembership(organizationId, account());
        long outsider = account();
        organizationOf(outsider);

        designate(outsider, organizationId, staff)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORGANIZATION-002"));
    }

    @Test
    @DisplayName("다른 발주사의 소속을 지정하면 404로 거부한다")
    void hidesMembershipOfOtherOrganization() throws Exception {
        long owner = account();
        long organizationId = organizationOf(owner);
        long otherOrganizationStaff = staffMembership(organizationOf(account()), account());

        designate(owner, organizationId, otherOrganizationStaff)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORGANIZATION-004"));
        assertThat(isOwner(otherOrganizationStaff)).isFalse();
    }

    @Test
    @DisplayName("양수가 아닌 식별자는 400으로 거부한다")
    void rejectsNonPositiveIdentifier() throws Exception {
        long owner = account();
        long organizationId = organizationOf(owner);

        designate(owner, organizationId, 0L)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-400"));
    }

    @Test
    @DisplayName("인증하지 않은 요청은 401로 거부한다")
    void rejectsUnauthenticatedRequest() throws Exception {
        mockMvc.perform(put("/api/v1/organizations/1/owners/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON-401"));
    }

    private ResultActions designate(long requester, long organizationId, long membershipId) throws Exception {
        return mockMvc.perform(
                put("/api/v1/organizations/{organizationId}/owners/{membershipId}", organizationId, membershipId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(requester)));
    }

    private static long account() {
        return ACCOUNT_IDS.incrementAndGet();
    }

    private long organizationOf(long founder) throws Exception {
        String body = mockMvc.perform(post("/api/v1/organizations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(founder))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "오르빗 설비", "industry", "HVAC"))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).path("result").path("organizationId").asLong();
    }

    private long staffMembership(long organizationId, long accountId) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(
                "insert into company_memberships"
                        + " (company_id, member_id, is_owner, status, joined_at, status_changed_at, created_at, updated_at)"
                        + " values (?, ?, false, 'ACTIVE', ?, ?, ?, ?)",
                organizationId,
                accountId,
                now,
                now,
                now,
                now);
        return membershipOf(organizationId, accountId);
    }

    private long membershipOf(long organizationId, long accountId) {
        return jdbcTemplate.queryForObject(
                "select id from company_memberships where company_id = ? and member_id = ?",
                Long.class,
                organizationId,
                accountId);
    }

    private boolean isOwner(long membershipId) {
        return jdbcTemplate.queryForObject(
                "select is_owner from company_memberships where id = ?", Boolean.class, membershipId);
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
