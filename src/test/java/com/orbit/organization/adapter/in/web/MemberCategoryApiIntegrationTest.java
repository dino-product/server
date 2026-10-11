package com.orbit.organization.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
@DisplayName("직원·기사 유형 API")
class MemberCategoryApiIntegrationTest extends IntegrationTestSupport {

    private static final AtomicLong ACCOUNT_IDS = new AtomicLong(50_000);
    private static final String STAFF = "staff-categories";
    private static final String TECHNICIAN = "technician-categories";

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
    @DisplayName("총관리자가 직원 유형을 만들면 공백을 지운 이름과 대문자 색상으로 저장하고 목록에 생성순으로 나온다")
    void ownerCreatesAndListsStaffCategories() throws Exception {
        long organizationId = organizationOfNewOwner();

        long first = createCategory(organizationId, STAFF, "  정규직 ", "#3b82f6");
        long second = createCategory(organizationId, STAFF, "계약직", "#EF4444");

        list(organizationId, STAFF)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.categories[*].categoryId").value(contains((int) first, (int) second)))
                .andExpect(jsonPath("$.result.categories[0].name").value("정규직"))
                .andExpect(jsonPath("$.result.categories[0].color").value("#3B82F6"));
        assertThat(jdbcTemplate.queryForMap(
                        "select company_id, name, color, is_deleted, deleted_at from company_membership_category"
                                + " where id = ?",
                        first))
                .containsEntry("company_id", organizationId)
                .containsEntry("name", "정규직")
                .containsEntry("color", "#3B82F6")
                .containsEntry("is_deleted", false)
                .containsEntry("deleted_at", null);
    }

    @Test
    @DisplayName("직원 유형과 기사 유형은 따로 관리해 같은 이름을 쓸 수 있고 서로의 목록에 나오지 않는다")
    void separatesStaffAndTechnicianCategories() throws Exception {
        long organizationId = organizationOfNewOwner();

        createCategory(organizationId, STAFF, "AS팀", "#3B82F6");
        createCategory(organizationId, TECHNICIAN, "AS팀", "#3B82F6");
        createCategory(organizationId, TECHNICIAN, "전기 기사", "#EF4444");

        list(organizationId, STAFF)
                .andExpect(jsonPath("$.result.categories.length()").value(1));
        list(organizationId, TECHNICIAN)
                .andExpect(jsonPath("$.result.categories[*].name").value(contains("AS팀", "전기 기사")));
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 10})
    @DisplayName("유형 이름은 2자와 10자를 받는다")
    void acceptsNameBoundaries(int length) throws Exception {
        long organizationId = organizationOfNewOwner();

        create(organizationId, STAFF, "가".repeat(length), "#3B82F6").andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 11})
    @DisplayName("1자이거나 11자인 유형 이름은 400으로 거부한다")
    void rejectsNameOutsideBoundaries(int length) throws Exception {
        long organizationId = organizationOfNewOwner();

        create(organizationId, STAFF, "가".repeat(length), "#3B82F6")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ORGANIZATION-007"));
    }

    @Test
    @DisplayName("NUL 문자가 들어간 유형 이름은 저장하지 않고 400으로 거부한다")
    void rejectsNameContainingNul() throws Exception {
        long organizationId = organizationOfNewOwner();

        create(organizationId, STAFF, "설치\0팀", "#3B82F6")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ORGANIZATION-007"));
    }

    @Test
    @DisplayName("프리셋에 없는 색상은 400으로 거부한다")
    void rejectsColorOutsidePalette() throws Exception {
        long organizationId = organizationOfNewOwner();

        create(organizationId, TECHNICIAN, "전기 기사", "#000000")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ORGANIZATION-007"));
    }

    @Test
    @DisplayName("삭제되지 않은 같은 종류의 유형과 이름이 같으면 409로 거부하고, 삭제된 유형과 같은 이름은 새 유형으로 만든다")
    void rejectsDuplicateNameButAllowsNameOfDeletedCategory() throws Exception {
        long organizationId = organizationOfNewOwner();
        long original = createCategory(organizationId, STAFF, "정규직", "#3B82F6");

        create(organizationId, STAFF, " 정규직 ", "#EF4444")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORGANIZATION-009"));

        remove(organizationId, STAFF, original).andExpect(status().isNoContent());
        long recreated = createCategory(organizationId, STAFF, "정규직", "#EF4444");

        assertThat(recreated).isNotEqualTo(original);
        list(organizationId, STAFF)
                .andExpect(jsonPath("$.result.categories[*].categoryId").value(contains((int) recreated)));
    }

    @Test
    @DisplayName("총관리자가 이름·색상을 바꾸면 바뀐 유형을 돌려주고, 다른 유형과 같은 이름으로는 바꿀 수 없다")
    void ownerUpdatesCategory() throws Exception {
        long organizationId = organizationOfNewOwner();
        long category = createCategory(organizationId, TECHNICIAN, "전기 기사", "#3B82F6");
        createCategory(organizationId, TECHNICIAN, "배관 기사", "#3B82F6");

        update(organizationId, TECHNICIAN, category, "냉난방 기사", "#22c55e")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.categoryId").value(category))
                .andExpect(jsonPath("$.result.name").value("냉난방 기사"))
                .andExpect(jsonPath("$.result.color").value("#22C55E"));
        update(organizationId, TECHNICIAN, category, "냉난방 기사", "#EF4444").andExpect(status().isOk());
        update(organizationId, TECHNICIAN, category, "배관 기사", "#EF4444")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORGANIZATION-009"));
    }

    @Test
    @DisplayName("삭제는 소프트 삭제이고 다시 삭제해도 성공하며, 삭제된 유형은 목록에서 빠지고 수정하면 404다")
    void softDeletesIdempotently() throws Exception {
        long organizationId = organizationOfNewOwner();
        long category = createCategory(organizationId, TECHNICIAN, "전기 기사", "#3B82F6");

        remove(organizationId, TECHNICIAN, category).andExpect(status().isNoContent());
        Timestamp deletedAt = jdbcTemplate.queryForObject(
                "select deleted_at from company_technician_category where id = ?", Timestamp.class, category);
        remove(organizationId, TECHNICIAN, category).andExpect(status().isNoContent());

        assertThat(jdbcTemplate.queryForMap(
                        "select is_deleted, deleted_at from company_technician_category where id = ?", category))
                .containsEntry("is_deleted", true)
                .containsEntry("deleted_at", deletedAt);
        list(organizationId, TECHNICIAN).andExpect(jsonPath("$.result.categories", empty()));
        update(organizationId, TECHNICIAN, category, "새 기사", "#3B82F6")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORGANIZATION-008"));
    }

    @Test
    @DisplayName("직원 유형을 삭제하면 그 유형이 지정된 직원 소속은 유형 없음이 되고 다른 유형은 그대로다")
    void deletingStaffCategoryUnassignsMemberships() throws Exception {
        long organizationId = organizationOfNewOwner();
        long deleted = createCategory(organizationId, STAFF, "정규직", "#3B82F6");
        long kept = createCategory(organizationId, STAFF, "계약직", "#3B82F6");
        long firstStaff = joinAsStaff(organizationId, ACCOUNT_IDS.incrementAndGet(), deleted);
        long secondStaff = joinAsStaff(organizationId, ACCOUNT_IDS.incrementAndGet(), kept);

        remove(organizationId, STAFF, deleted).andExpect(status().isNoContent());

        assertThat(staffCategoryOf(firstStaff)).isNull();
        assertThat(staffCategoryOf(secondStaff)).isEqualTo(kept);
    }

    @Test
    @DisplayName("기사 유형을 삭제하면 그 유형이 지정된 기사 계약은 유형 없음이 되고 다른 유형은 그대로다")
    void deletingTechnicianCategoryUnassignsTechnicians() throws Exception {
        long organizationId = organizationOfNewOwner();
        long deleted = createCategory(organizationId, TECHNICIAN, "전기 기사", "#3B82F6");
        long kept = createCategory(organizationId, TECHNICIAN, "배관 기사", "#3B82F6");
        long firstTechnician = joinAsTechnician(organizationId, ACCOUNT_IDS.incrementAndGet(), deleted);
        long secondTechnician = joinAsTechnician(organizationId, ACCOUNT_IDS.incrementAndGet(), kept);

        remove(organizationId, TECHNICIAN, deleted).andExpect(status().isNoContent());

        assertThat(technicianCategoryOf(firstTechnician)).isNull();
        assertThat(technicianCategoryOf(secondTechnician)).isEqualTo(kept);
    }

    @Test
    @DisplayName("총관리자가 아닌 직원은 유형 목록을 볼 수 있지만 만들거나 바꾸거나 지울 수 없다")
    void staffListsButCannotChangeCategories() throws Exception {
        long organizationId = organizationOfNewOwner();
        long category = createCategory(organizationId, TECHNICIAN, "전기 기사", "#3B82F6");
        joinAsStaff(organizationId, requester(), null);

        list(organizationId, TECHNICIAN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.categories[0].name").value("전기 기사"));
        create(organizationId, TECHNICIAN, "배관 기사", "#3B82F6")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORGANIZATION-003"));
        update(organizationId, TECHNICIAN, category, "배관 기사", "#3B82F6")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORGANIZATION-003"));
        remove(organizationId, TECHNICIAN, category)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORGANIZATION-003"));
    }

    @Test
    @DisplayName("그 발주사에 소속이 없는 계정은 유형 목록을 볼 수 없다")
    void rejectsNonMemberListing() throws Exception {
        long organizationId = organizationOfNewOwner();
        requester();

        list(organizationId, STAFF)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORGANIZATION-002"));
    }

    @Test
    @DisplayName("다른 발주사의 유형은 그 발주사 경로로는 403, 내 발주사 경로로는 없는 유형과 같은 404다")
    void isolatesCategoriesBetweenOrganizations() throws Exception {
        long organizationId = organizationOfNewOwner();
        long category = createCategory(organizationId, STAFF, "정규직", "#3B82F6");
        long otherOrganizationId = organizationOfNewOwner();

        update(organizationId, STAFF, category, "계약직", "#3B82F6")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORGANIZATION-002"));
        update(otherOrganizationId, STAFF, category, "계약직", "#3B82F6")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORGANIZATION-008"));
        remove(otherOrganizationId, STAFF, category)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORGANIZATION-008"));
        assertThat(jdbcTemplate.queryForObject(
                        "select is_deleted from company_membership_category where id = ?", Boolean.class, category))
                .isFalse();
    }

    @Test
    @DisplayName("직원 유형 식별자로 같은 번호의 기사 유형을 지울 수 없다")
    void doesNotMixKindsByIdentifier() throws Exception {
        long organizationId = organizationOfNewOwner();
        long staffCategory = createCategory(organizationId, STAFF, "정규직", "#3B82F6");

        remove(organizationId, TECHNICIAN, staffCategory)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORGANIZATION-008"));
    }

    @Test
    @DisplayName("숫자가 아닌 유형 식별자는 권한 확인 전에 400으로 거부한다")
    void rejectsNonNumericCategoryIdentifier() throws Exception {
        long otherOrganizationId = organizationOfNewOwner();
        requester();

        mockMvc.perform(put(
                                "/api/v1/organizations/{organizationId}/" + STAFF + "/{categoryId}",
                                otherOrganizationId,
                                "abc")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "정규직", "color", "#3B82F6"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-400"));
        mockMvc.perform(delete(
                                "/api/v1/organizations/{organizationId}/" + TECHNICIAN + "/{categoryId}",
                                otherOrganizationId,
                                "abc")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-400"));
    }

    @Test
    @DisplayName("없는 유형의 수정·삭제는 404다")
    void rejectsMissingCategory() throws Exception {
        long organizationId = organizationOfNewOwner();
        long missing = createCategory(organizationId, STAFF, "정규직", "#3B82F6") + 1_000;

        update(organizationId, STAFF, missing, "계약직", "#3B82F6")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORGANIZATION-008"));
        remove(organizationId, STAFF, missing)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORGANIZATION-008"));
    }

    @Test
    @DisplayName("인증하지 않은 요청은 401로 거부한다")
    void rejectsUnauthenticatedRequest() throws Exception {
        mockMvc.perform(get("/api/v1/organizations/1/staff-categories"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON-401"));
    }

    private long organizationOfNewOwner() throws Exception {
        requester();
        return createOrganization();
    }

    private long createOrganization() throws Exception {
        String body = mockMvc.perform(post("/api/v1/organizations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "오르빗 설비", "industry", "HVAC"))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).path("result").path("organizationId").asLong();
    }

    private long createCategory(long organizationId, String kind, String name, String color) throws Exception {
        String body = create(organizationId, kind, name, color)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).path("result").path("categoryId").asLong();
    }

    /** 참여 요청 승인(HM-290)과 유형 지정이 아직 없어 활성 직원 소속을 직접 넣는다. */
    private long joinAsStaff(long organizationId, long accountId, Long staffCategoryId) {
        Timestamp joinedAt = Timestamp.from(Instant.parse("2026-10-06T00:00:00Z"));
        jdbcTemplate.update(
                "insert into company_memberships"
                        + " (company_id, member_id, is_owner, status, company_membership_category_id,"
                        + " joined_at, status_changed_at, created_at, updated_at)"
                        + " values (?, ?, false, 'ACTIVE', ?, ?, ?, ?, ?)",
                organizationId,
                accountId,
                staffCategoryId,
                joinedAt,
                joinedAt,
                joinedAt,
                joinedAt);
        return jdbcTemplate.queryForObject(
                "select id from company_memberships where company_id = ? and member_id = ?",
                Long.class,
                organizationId,
                accountId);
    }

    /** 참여 요청 승인(HM-290)과 유형 지정(HM-292)이 아직 없어 활성 기사 계약을 직접 넣는다. */
    private long joinAsTechnician(long organizationId, long accountId, Long technicianCategoryId) {
        Timestamp joinedAt = Timestamp.from(Instant.parse("2026-10-06T00:00:00Z"));
        jdbcTemplate.update(
                "insert into technician"
                        + " (company_id, member_id, company_technician_category_id, status, status_changed_at,"
                        + " created_at, updated_at)"
                        + " values (?, ?, ?, 'ACTIVE', ?, ?, ?)",
                organizationId,
                accountId,
                technicianCategoryId,
                joinedAt,
                joinedAt,
                joinedAt);
        return jdbcTemplate.queryForObject(
                "select id from technician where company_id = ? and member_id = ?",
                Long.class,
                organizationId,
                accountId);
    }

    private Long technicianCategoryOf(long technicianId) {
        return jdbcTemplate.queryForObject(
                "select company_technician_category_id from technician where id = ?", Long.class, technicianId);
    }

    private Long staffCategoryOf(long membershipId) {
        return jdbcTemplate.queryForObject(
                "select company_membership_category_id from company_memberships where id = ?",
                Long.class,
                membershipId);
    }

    private ResultActions list(long organizationId, String kind) throws Exception {
        return mockMvc.perform(get("/api/v1/organizations/{organizationId}/" + kind, organizationId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken()));
    }

    private ResultActions create(long organizationId, String kind, String name, String color) throws Exception {
        return mockMvc.perform(post("/api/v1/organizations/{organizationId}/" + kind, organizationId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", name, "color", color))));
    }

    private ResultActions update(long organizationId, String kind, long categoryId, String name, String color)
            throws Exception {
        return mockMvc.perform(
                put("/api/v1/organizations/{organizationId}/" + kind + "/{categoryId}", organizationId, categoryId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", name, "color", color))));
    }

    private ResultActions remove(long organizationId, String kind, long categoryId) throws Exception {
        return mockMvc.perform(
                delete("/api/v1/organizations/{organizationId}/" + kind + "/{categoryId}", organizationId, categoryId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken()));
    }

    private long requester() {
        requesterAccountId = ACCOUNT_IDS.incrementAndGet();
        return requesterAccountId;
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
