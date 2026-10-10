package com.orbit.organization.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
@DisplayName("발주사 API")
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

    @Test
    @DisplayName("총관리자는 발주사 ID·발주사명·업종을 조회한다")
    void ownerReadsOrganization() throws Exception {
        requester();
        long organizationId = createOrganization("오르빗 설비", "PLUMBING");

        read(organizationId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.organizationId").value(organizationId))
                .andExpect(jsonPath("$.result.name").value("오르빗 설비"))
                .andExpect(jsonPath("$.result.industry").value("PLUMBING"))
                .andExpect(jsonPath("$.result.companyCode").doesNotExist());
    }

    @Test
    @DisplayName("총관리자가 아닌 직원의 조회는 403으로 거부한다")
    void rejectsStaffReadingOrganization() throws Exception {
        requester();
        long organizationId = createOrganization("오르빗 설비", "HVAC");
        long staffAccountId = requester();
        joinAsStaff(organizationId, staffAccountId);

        read(organizationId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORGANIZATION-003"));
    }

    @Test
    @DisplayName("소속되지 않은 계정의 조회는 403으로 거부한다")
    void rejectsNonMemberReadingOrganization() throws Exception {
        requester();
        long organizationId = createOrganization("오르빗 설비", "HVAC");
        requester();

        read(organizationId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORGANIZATION-002"));
    }

    @Test
    @DisplayName("다른 발주사의 총관리자도 이 발주사 정보는 볼 수 없다")
    void rejectsOwnerOfAnotherOrganization() throws Exception {
        requester();
        long organizationId = createOrganization("오르빗 설비", "HVAC");
        requester();
        createOrganization("다른 발주사", "HVAC");

        read(organizationId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORGANIZATION-002"));
    }

    @Test
    @DisplayName("없는 발주사는 존재 여부를 드러내지 않고 403으로 거부한다")
    void rejectsUnknownOrganizationWithoutRevealingExistence() throws Exception {
        requester();
        createOrganization("오르빗 설비", "HVAC");

        read(Long.MAX_VALUE)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORGANIZATION-002"));
    }

    @Test
    @DisplayName("숫자가 아닌 발주사 식별자는 400으로 거부한다")
    void rejectsNonNumericOrganizationId() throws Exception {
        requester();

        mockMvc.perform(get("/api/v1/organizations/abc").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-400"));
    }

    @Test
    @DisplayName("인증하지 않은 조회는 401로 거부한다")
    void rejectsUnauthenticatedRead() throws Exception {
        mockMvc.perform(get("/api/v1/organizations/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON-401"));
    }

    @Test
    @DisplayName("총관리자가 발주사명·업종을 바꾸면 저장되고 조회에도 반영된다")
    void ownerUpdatesOrganization() throws Exception {
        requester();
        long organizationId = createOrganization("오르빗 설비", "HVAC");

        update(organizationId, Map.of("name", "  새 발주사  ", "industry", "APPLIANCE_SERVICE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.organizationId").value(organizationId))
                .andExpect(jsonPath("$.result.name").value("새 발주사"))
                .andExpect(jsonPath("$.result.industry").value("APPLIANCE_SERVICE"));

        assertThat(jdbcTemplate.queryForMap("select name, industry_code from companies where id = ?", organizationId))
                .containsEntry("name", "새 발주사")
                .containsEntry("industry_code", "APPLIANCE_SERVICE");
        read(organizationId).andExpect(jsonPath("$.result.name").value("새 발주사"));
    }

    @Test
    @DisplayName("다른 발주사와 같은 이름으로 바꿀 수 있다")
    void allowsNameUsedByAnotherOrganization() throws Exception {
        requester();
        createOrganization("같은 이름 설비", "HVAC");
        long organizationId = createOrganization("오르빗 설비", "HVAC");

        update(organizationId, Map.of("name", "같은 이름 설비", "industry", "HVAC")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("발주사명은 2자·30자까지 받고 1자·31자·공백만·이모지는 400으로 거부한다")
    void validatesNameLikeCreation() throws Exception {
        requester();
        long organizationId = createOrganization("오르빗 설비", "HVAC");

        update(organizationId, Map.of("name", "가".repeat(2), "industry", "HVAC"))
                .andExpect(status().isOk());
        update(organizationId, Map.of("name", "가".repeat(30), "industry", "HVAC"))
                .andExpect(status().isOk());
        for (String name : new String[] {"가", "가".repeat(31), "   ", "오르빗\uD83D\uDE00"}) {
            update(organizationId, Map.of("name", name, "industry", "HVAC"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("ORGANIZATION-001"));
        }
        assertThat(jdbcTemplate.queryForObject("select name from companies where id = ?", String.class, organizationId))
                .isEqualTo("가".repeat(30));
    }

    @Test
    @DisplayName("업종이 없으면 ORGANIZATION-001, 6종에 없는 업종이면 COMMON-400으로 거부한다")
    void rejectsMissingOrUnknownIndustry() throws Exception {
        requester();
        long organizationId = createOrganization("오르빗 설비", "HVAC");

        update(organizationId, Map.of("name", "새 발주사"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ORGANIZATION-001"));
        update(organizationId, Map.of("name", "새 발주사", "industry", "AGRICULTURE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-400"));
    }

    @Test
    @DisplayName("총관리자가 아닌 직원의 수정은 403으로 거부하고 저장하지 않는다")
    void rejectsStaffUpdatingOrganization() throws Exception {
        requester();
        long organizationId = createOrganization("오르빗 설비", "HVAC");
        joinAsStaff(organizationId, requester());

        update(organizationId, Map.of("name", "새 발주사", "industry", "HVAC"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORGANIZATION-003"));
        assertThat(jdbcTemplate.queryForObject("select name from companies where id = ?", String.class, organizationId))
                .isEqualTo("오르빗 설비");
    }

    @Test
    @DisplayName("다른 발주사의 총관리자의 수정은 403으로 거부한다")
    void rejectsOwnerOfAnotherOrganizationUpdating() throws Exception {
        requester();
        long organizationId = createOrganization("오르빗 설비", "HVAC");
        requester();
        createOrganization("다른 발주사", "HVAC");

        update(organizationId, Map.of("name", "새 발주사", "industry", "HVAC"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORGANIZATION-002"));
    }

    @Test
    @DisplayName("총관리자는 발주사를 만들 때 받은 현재 회사 코드를 조회한다")
    void ownerReadsCurrentCompanyCode() throws Exception {
        requester();
        String body = create(Map.of("name", "오르빗 설비", "industry", "HVAC"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long organizationId = objectMapper
                .readTree(body)
                .path("result")
                .path("organizationId")
                .asLong();
        String issuedCode =
                objectMapper.readTree(body).path("result").path("companyCode").asText();

        readCompanyCode(organizationId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.companyCode").value(issuedCode));
    }

    @Test
    @DisplayName("총관리자가 아닌 직원의 회사 코드 조회는 403으로 거부한다")
    void rejectsStaffReadingCompanyCode() throws Exception {
        requester();
        long organizationId = createOrganization("오르빗 설비", "HVAC");
        joinAsStaff(organizationId, requester());

        readCompanyCode(organizationId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORGANIZATION-003"));
    }

    @Test
    @DisplayName("소속되지 않은 계정의 회사 코드 조회는 403으로 거부한다")
    void rejectsNonMemberReadingCompanyCode() throws Exception {
        requester();
        long organizationId = createOrganization("오르빗 설비", "HVAC");
        requester();

        readCompanyCode(organizationId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORGANIZATION-002"));
    }

    @Test
    @DisplayName("인증하지 않은 회사 코드 조회는 401로 거부한다")
    void rejectsUnauthenticatedCompanyCodeRead() throws Exception {
        mockMvc.perform(get("/api/v1/organizations/1/company-code"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON-401"));
    }

    @Test
    @DisplayName("총관리자가 회사 코드를 바꾸면 새 코드가 현재 코드가 되고 이전 코드는 폐기 코드로 남는다")
    void ownerChangesCompanyCode() throws Exception {
        requester();
        long organizationId = createOrganization("오르빗 설비", "HVAC");
        String previousCode = currentCode(organizationId);

        String newCode = objectMapper
                .readTree(changeCompanyCode(organizationId, accessToken())
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.result.companyCode")
                                .value(matchesPattern("[0-9ABCDEFGHJKMNPQRSTVWXYZ]{6}")))
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .path("result")
                .path("companyCode")
                .asText();

        assertThat(newCode).isNotEqualTo(previousCode);
        assertThat(currentCode(organizationId)).isEqualTo(newCode);
        assertThat(retiredCodes(organizationId)).containsExactly(previousCode);
        readCompanyCode(organizationId)
                .andExpect(jsonPath("$.result.companyCode").value(newCode));
    }

    @Test
    @DisplayName("회사 코드를 바꿔도 기존 직원 소속은 그대로 유지된다")
    void keepsExistingMembershipsAfterCodeChange() throws Exception {
        long ownerAccountId = requester();
        long organizationId = createOrganization("오르빗 설비", "HVAC");
        long staffAccountId = requester();
        joinAsStaff(organizationId, staffAccountId);
        requesterAccountId = ownerAccountId;

        changeCompanyCode(organizationId, accessToken()).andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForList(
                        "select member_id, status from company_memberships where company_id = ? order by member_id",
                        organizationId))
                .extracting(row -> row.get("member_id"), row -> row.get("status"))
                .containsExactly(tuple(ownerAccountId, "ACTIVE"), tuple(staffAccountId, "ACTIVE"));
    }

    @Test
    @DisplayName("두 총관리자가 동시에 바꿔도 차례로 재발급되어 둘 다 성공하고 같은 코드를 두 번 폐기하지 않는다")
    void serializesConcurrentCodeChanges() throws Exception {
        requester();
        long organizationId = createOrganization("오르빗 설비", "HVAC");
        String firstOwnerToken = accessToken();
        joinAsOwner(organizationId, requester());
        String secondOwnerToken = accessToken();
        String createdCode = currentCode(organizationId);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> statuses = new ArrayList<>();
            for (String token : List.of(firstOwnerToken, secondOwnerToken)) {
                statuses.add(executor.submit(() -> {
                    start.await();
                    return changeCompanyCode(organizationId, token)
                            .andReturn()
                            .getResponse()
                            .getStatus();
                }));
            }
            start.countDown();
            for (Future<Integer> status : statuses) {
                assertThat(status.get(30, TimeUnit.SECONDS)).isEqualTo(200);
            }
        } finally {
            executor.shutdownNow();
        }

        List<String> retired = retiredCodes(organizationId);
        assertThat(retired).hasSize(2).doesNotHaveDuplicates().contains(createdCode);
        assertThat(retired).doesNotContain(currentCode(organizationId));
    }

    @Test
    @DisplayName("총관리자가 아닌 직원의 회사 코드 변경은 403으로 거부하고 코드를 바꾸지 않는다")
    void rejectsStaffChangingCompanyCode() throws Exception {
        requester();
        long organizationId = createOrganization("오르빗 설비", "HVAC");
        String code = currentCode(organizationId);
        joinAsStaff(organizationId, requester());

        changeCompanyCode(organizationId, accessToken())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORGANIZATION-003"));
        assertThat(currentCode(organizationId)).isEqualTo(code);
        assertThat(retiredCodes(organizationId)).isEmpty();
    }

    @Test
    @DisplayName("소속되지 않은 계정의 회사 코드 변경은 403으로 거부한다")
    void rejectsNonMemberChangingCompanyCode() throws Exception {
        requester();
        long organizationId = createOrganization("오르빗 설비", "HVAC");
        requester();

        changeCompanyCode(organizationId, accessToken())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORGANIZATION-002"));
        assertThat(retiredCodes(organizationId)).isEmpty();
    }

    private long createOrganization(String name, String industry) throws Exception {
        String body = create(Map.of("name", name, "industry", industry))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).path("result").path("organizationId").asLong();
    }

    /** 참여 요청 승인(HM-290)이 아직 없어 총관리자가 아닌 활성 직원 소속을 직접 넣는다. */
    private void joinAsStaff(long organizationId, long accountId) {
        insertActiveMembership(organizationId, accountId, false);
    }

    /** 총관리자 지정(HM-294)이 이 기준선에 없어 추가 총관리자 소속을 직접 넣는다. */
    private void joinAsOwner(long organizationId, long accountId) {
        insertActiveMembership(organizationId, accountId, true);
    }

    private void insertActiveMembership(long organizationId, long accountId, boolean owner) {
        Instant joinedAt = Instant.parse("2026-10-06T00:00:00Z");
        jdbcTemplate.update(
                "insert into company_memberships"
                        + " (company_id, member_id, is_owner, status,"
                        + " joined_at, status_changed_at, created_at, updated_at)"
                        + " values (?, ?, ?, 'ACTIVE', ?, ?, ?, ?)",
                organizationId,
                accountId,
                owner,
                Timestamp.from(joinedAt),
                Timestamp.from(joinedAt),
                Timestamp.from(joinedAt),
                Timestamp.from(joinedAt));
    }

    private String currentCode(long organizationId) {
        return jdbcTemplate.queryForObject("select code from companies where id = ?", String.class, organizationId);
    }

    private List<String> retiredCodes(long organizationId) {
        return jdbcTemplate.queryForList(
                "select code from retired_company_codes where company_id = ? order by retired_at, id",
                String.class,
                organizationId);
    }

    private ResultActions changeCompanyCode(long organizationId, String accessToken) throws Exception {
        return mockMvc.perform(post("/api/v1/organizations/{organizationId}/company-code", organizationId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
    }

    private ResultActions update(long organizationId, Map<String, String> request) throws Exception {
        return mockMvc.perform(put("/api/v1/organizations/{organizationId}", organizationId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    private ResultActions readCompanyCode(long organizationId) throws Exception {
        return mockMvc.perform(get("/api/v1/organizations/{organizationId}/company-code", organizationId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken()));
    }

    private ResultActions read(long organizationId) throws Exception {
        return mockMvc.perform(get("/api/v1/organizations/{organizationId}", organizationId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken()));
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
