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
import com.orbit.support.SignupTestSupport;

import tools.jackson.databind.ObjectMapper;

/** 요청자는 실제 Access Token 인증을 거친 auth의 {@code AccountPrincipal}에서 얻는다. 계정마다 다른 {@code sub}로 토큰을 만든다. */
@AutoConfigureMockMvc
@DisplayName("작업 유형 API")
class WorkTypeApiIntegrationTest extends IntegrationTestSupport {

    private static final AtomicLong ACCOUNT_IDS = new AtomicLong(80_000);

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

    private final SignupTestSupport.SignupOnce signups = new SignupTestSupport.SignupOnce();

    @Test
    @DisplayName("총관리자가 작업 유형을 만들면 공백을 지운 이름과 대문자 색상으로 저장하고 목록에 생성순으로 나온다")
    void ownerCreatesAndListsWorkTypes() throws Exception {
        long organizationId = organizationOfNewOwner();

        long first = createWorkType(organizationId, "  설치 ", "#3b82f6");
        long second = createWorkType(organizationId, "수리", "#EF4444");

        list(organizationId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.workTypes[*].workTypeId").value(contains((int) first, (int) second)))
                .andExpect(jsonPath("$.result.workTypes[0].name").value("설치"))
                .andExpect(jsonPath("$.result.workTypes[0].color").value("#3B82F6"));
        assertThat(jdbcTemplate.queryForMap(
                        "select company_id, name, color, is_deleted, deleted_at from work_type where id = ?", first))
                .containsEntry("company_id", organizationId)
                .containsEntry("name", "설치")
                .containsEntry("color", "#3B82F6")
                .containsEntry("is_deleted", false)
                .containsEntry("deleted_at", null);
    }

    @Test
    @DisplayName("작업 유형은 직원·기사 유형과 따로 관리해 같은 이름을 쓸 수 있다")
    void separatesWorkTypesFromMemberCategories() throws Exception {
        long organizationId = organizationOfNewOwner();
        mockMvc.perform(post("/api/v1/organizations/{organizationId}/staff-categories", organizationId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "설치", "color", "#3B82F6"))))
                .andExpect(status().isOk());

        create(organizationId, "설치", "#3B82F6").andExpect(status().isOk());

        list(organizationId).andExpect(jsonPath("$.result.workTypes[*].name").value(contains("설치")));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 11})
    @DisplayName("1자이거나 11자인 작업 유형 이름은 400으로 거부한다")
    void rejectsNameOutsideBoundaries(int length) throws Exception {
        long organizationId = organizationOfNewOwner();

        create(organizationId, "가".repeat(length), "#3B82F6")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ORGANIZATION-007"));
    }

    @Test
    @DisplayName("프리셋에 없는 색상은 400으로 거부한다")
    void rejectsColorOutsidePalette() throws Exception {
        long organizationId = organizationOfNewOwner();

        create(organizationId, "설치", "#000000")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ORGANIZATION-007"));
    }

    @Test
    @DisplayName("삭제되지 않은 작업 유형과 이름이 같으면 409로 거부하고, 대소문자만 다른 이름은 허용한다")
    void rejectsDuplicateNameButAllowsDifferentCase() throws Exception {
        long organizationId = organizationOfNewOwner();
        createWorkType(organizationId, "AS점검", "#3B82F6");

        create(organizationId, " AS점검 ", "#EF4444")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORGANIZATION-009"));
        create(organizationId, "as점검", "#EF4444").andExpect(status().isOk());
    }

    @Test
    @DisplayName("총관리자가 이름·색상을 바꾸면 바뀐 작업 유형을 돌려주고, 다른 작업 유형과 같은 이름으로는 바꿀 수 없다")
    void ownerUpdatesWorkType() throws Exception {
        long organizationId = organizationOfNewOwner();
        long workType = createWorkType(organizationId, "설치", "#3B82F6");
        createWorkType(organizationId, "수리", "#3B82F6");

        update(organizationId, workType, "점검", "#22c55e")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.workTypeId").value(workType))
                .andExpect(jsonPath("$.result.name").value("점검"))
                .andExpect(jsonPath("$.result.color").value("#22C55E"));
        update(organizationId, workType, "수리", "#EF4444")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORGANIZATION-009"));
    }

    @Test
    @DisplayName("총관리자가 아닌 직원은 작업 유형 목록을 볼 수 있지만 만들거나 바꿀 수 없다")
    void staffListsButCannotChangeWorkTypes() throws Exception {
        long organizationId = organizationOfNewOwner();
        long workType = createWorkType(organizationId, "설치", "#3B82F6");
        joinAsStaff(organizationId, requester());

        list(organizationId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.workTypes[0].name").value("설치"));
        create(organizationId, "수리", "#3B82F6")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORGANIZATION-003"));
        update(organizationId, workType, "수리", "#3B82F6")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORGANIZATION-003"));
    }

    @Test
    @DisplayName("기사는 작업 유형 목록을 볼 수 없고 만들 수도 없다")
    void technicianCannotListOrCreateWorkTypes() throws Exception {
        long organizationId = organizationOfNewOwner();
        joinAsTechnician(organizationId, requester());

        list(organizationId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORGANIZATION-002"));
        create(organizationId, "설치", "#3B82F6")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORGANIZATION-002"));
    }

    @Test
    @DisplayName("다른 발주사의 작업 유형은 그 발주사 경로로는 403, 내 발주사 경로로는 없는 유형과 같은 404다")
    void isolatesWorkTypesBetweenOrganizations() throws Exception {
        long organizationId = organizationOfNewOwner();
        long workType = createWorkType(organizationId, "설치", "#3B82F6");
        long otherOrganizationId = organizationOfNewOwner();

        update(organizationId, workType, "수리", "#3B82F6")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORGANIZATION-002"));
        update(otherOrganizationId, workType, "수리", "#3B82F6")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORGANIZATION-008"));
    }

    @Test
    @DisplayName("숫자가 아닌 작업 유형 식별자는 권한 확인 전에 400으로 거부한다")
    void rejectsNonNumericWorkTypeIdentifier() throws Exception {
        long otherOrganizationId = organizationOfNewOwner();
        requester();

        mockMvc.perform(put(
                                "/api/v1/organizations/{organizationId}/work-types/{workTypeId}",
                                otherOrganizationId,
                                "abc")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "설치", "color", "#3B82F6"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-400"));
    }

    @Test
    @DisplayName("TC-28: 삭제 영향은 시작 전 작업과 시작한 작업으로 나뉘고, 삭제하면 시작 전 작업만 유형 없음이 되며 유형은 목록에서 빠진다")
    void deletesWorkTypeReleasingOnlyWorksBeforeStart() throws Exception {
        long organizationId = organizationOfNewOwner();
        long workType = createWorkType(organizationId, "설치", "#3B82F6");
        long kept = createWorkType(organizationId, "수리", "#EF4444");
        long registered = insertWork(organizationId, workType, "REGISTERED", null);
        long pending = insertWork(organizationId, workType, "PENDING_ACCEPT", 21L);
        long accepted = insertWork(organizationId, workType, "ACCEPTED", 22L);
        long inProgress = insertWork(organizationId, workType, "IN_PROGRESS", 23L);
        long completed = insertWork(organizationId, workType, "COMPLETED", 24L);
        long cancelled = insertWork(organizationId, workType, "CANCELED", null);
        long otherType = insertWork(organizationId, kept, "REGISTERED", null);

        deletionImpact(organizationId, workType)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.releasedCount").value(3))
                .andExpect(jsonPath("$.result.keptCount").value(3));
        remove(organizationId, workType).andExpect(status().isNoContent());

        for (long work : new long[] {registered, pending, accepted}) {
            assertThat(workTypeOf(work)).isNull();
        }
        for (long work : new long[] {inProgress, completed, cancelled}) {
            assertThat(workTypeOf(work)).isEqualTo(workType);
        }
        assertThat(workTypeOf(otherType)).isEqualTo(kept);
        assertThat(jdbcTemplate.queryForMap("select name, color, is_deleted from work_type where id = ?", workType))
                .containsEntry("name", "설치")
                .containsEntry("color", "#3B82F6")
                .containsEntry("is_deleted", true);
        list(organizationId)
                .andExpect(jsonPath("$.result.workTypes[*].workTypeId").value(contains((int) kept)));
    }

    @Test
    @DisplayName("삭제는 다시 해도 성공하고, 삭제된 작업 유형의 수정·영향 조회는 404다")
    void softDeletesIdempotently() throws Exception {
        long organizationId = organizationOfNewOwner();
        long workType = createWorkType(organizationId, "설치", "#3B82F6");

        remove(organizationId, workType).andExpect(status().isNoContent());
        remove(organizationId, workType).andExpect(status().isNoContent());

        list(organizationId).andExpect(jsonPath("$.result.workTypes", empty()));
        update(organizationId, workType, "수리", "#3B82F6")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORGANIZATION-008"));
        deletionImpact(organizationId, workType)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORGANIZATION-008"));
    }

    @Test
    @DisplayName("EC-20: 삭제된 작업 유형과 같은 이름은 새 유형으로 만들고, 삭제된 유형을 쓰던 작업은 옮기지 않는다")
    void recreatesSameNameAsNewWorkType() throws Exception {
        long organizationId = organizationOfNewOwner();
        long original = createWorkType(organizationId, "설치", "#3B82F6");
        long completed = insertWork(organizationId, original, "COMPLETED", 21L);
        remove(organizationId, original).andExpect(status().isNoContent());

        long recreated = createWorkType(organizationId, "설치", "#EF4444");

        assertThat(recreated).isNotEqualTo(original);
        assertThat(workTypeOf(completed)).isEqualTo(original);
        list(organizationId)
                .andExpect(jsonPath("$.result.workTypes[*].workTypeId").value(contains((int) recreated)));
    }

    @Test
    @DisplayName("총관리자가 아닌 직원은 작업 유형의 삭제 영향을 보거나 지울 수 없다")
    void staffCannotDeleteWorkType() throws Exception {
        long organizationId = organizationOfNewOwner();
        long workType = createWorkType(organizationId, "설치", "#3B82F6");
        joinAsStaff(organizationId, requester());

        deletionImpact(organizationId, workType)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORGANIZATION-003"));
        remove(organizationId, workType)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORGANIZATION-003"));
        assertThat(jdbcTemplate.queryForObject(
                        "select is_deleted from work_type where id = ?", Boolean.class, workType))
                .isFalse();
    }

    @Test
    @DisplayName("다른 발주사 경로로 지우면 없는 유형과 같은 404이고 삭제되지 않는다")
    void rejectsDeletingWorkTypeOfOtherOrganization() throws Exception {
        long organizationId = organizationOfNewOwner();
        long workType = createWorkType(organizationId, "설치", "#3B82F6");
        long otherOrganizationId = organizationOfNewOwner();

        remove(otherOrganizationId, workType)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORGANIZATION-008"));
        assertThat(jdbcTemplate.queryForObject(
                        "select is_deleted from work_type where id = ?", Boolean.class, workType))
                .isFalse();
    }

    @Test
    @DisplayName("인증하지 않은 요청은 401로 거부한다")
    void rejectsUnauthenticatedRequest() throws Exception {
        mockMvc.perform(get("/api/v1/organizations/1/work-types"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON-401"));
    }

    private long organizationOfNewOwner() throws Exception {
        requester();
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

    private long createWorkType(long organizationId, String name, String color) throws Exception {
        String body = create(organizationId, name, color)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).path("result").path("workTypeId").asLong();
    }

    /** 참여 요청 승인(HM-290)이 아직 develop에 없어 활성 직원 소속을 직접 넣는다. */
    private void joinAsStaff(long organizationId, long accountId) {
        Timestamp joinedAt = Timestamp.from(Instant.parse("2026-10-06T00:00:00Z"));
        jdbcTemplate.update(
                "insert into company_memberships"
                        + " (company_id, member_id, is_owner, status, joined_at, status_changed_at, created_at,"
                        + " updated_at) values (?, ?, false, 'ACTIVE', ?, ?, ?, ?)",
                organizationId,
                accountId,
                joinedAt,
                joinedAt,
                joinedAt,
                joinedAt);
    }

    /** 참여 요청 승인(HM-290)이 아직 develop에 없어 활성 기사 계약을 직접 넣는다. */
    private void joinAsTechnician(long organizationId, long accountId) {
        Timestamp joinedAt = Timestamp.from(Instant.parse("2026-10-06T00:00:00Z"));
        jdbcTemplate.update(
                "insert into technician (company_id, member_id, status, status_changed_at, created_at, updated_at)"
                        + " values (?, ?, 'ACTIVE', ?, ?, ?)",
                organizationId,
                accountId,
                joinedAt,
                joinedAt,
                joinedAt);
    }

    private ResultActions list(long organizationId) throws Exception {
        return mockMvc.perform(get("/api/v1/organizations/{organizationId}/work-types", organizationId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken()));
    }

    private ResultActions create(long organizationId, String name, String color) throws Exception {
        return mockMvc.perform(post("/api/v1/organizations/{organizationId}/work-types", organizationId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", name, "color", color))));
    }

    private ResultActions update(long organizationId, long workTypeId, String name, String color) throws Exception {
        return mockMvc.perform(
                put("/api/v1/organizations/{organizationId}/work-types/{workTypeId}", organizationId, workTypeId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", name, "color", color))));
    }

    /** schedule의 작업 등록·배정 흐름 대신 작업 행을 직접 넣는다. 유형 해제는 작업 행만 보므로 배정 이력은 두지 않는다. */
    private long insertWork(long organizationId, long workTypeId, String status, Long technicianId) {
        Timestamp at = Timestamp.from(Instant.parse("2026-10-06T00:00:00Z"));
        jdbcTemplate.update(
                "insert into work (version, company_id, created_by, registered_at, job_type_id, name, status,"
                        + " technician_id, created_at, updated_at) values (0, ?, 1, ?, ?, '작업', ?, ?, ?, ?)",
                organizationId,
                at,
                workTypeId,
                status,
                technicianId,
                at,
                at);
        return jdbcTemplate.queryForObject("select max(id) from work where company_id = ?", Long.class, organizationId);
    }

    private Long workTypeOf(long workId) {
        return jdbcTemplate.queryForObject("select job_type_id from work where id = ?", Long.class, workId);
    }

    private ResultActions deletionImpact(long organizationId, long workTypeId) throws Exception {
        return mockMvc.perform(get(
                        "/api/v1/organizations/{organizationId}/work-types/{workTypeId}/deletion-impact",
                        organizationId,
                        workTypeId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken()));
    }

    private ResultActions remove(long organizationId, long workTypeId) throws Exception {
        return mockMvc.perform(
                delete("/api/v1/organizations/{organizationId}/work-types/{workTypeId}", organizationId, workTypeId)
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
        String accessToken = jwt.serialize();
        signups.ensure(mockMvc, objectMapper, requesterAccountId, accessToken);
        return accessToken;
    }
}
