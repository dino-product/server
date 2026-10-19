package com.orbit.organization.adapter.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

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
 * 총관리자의 참여 인원 API. 요청자는 실제 Access Token 인증을 거친 auth의 {@code AccountPrincipal}에서 얻고, 발주사·직원 소속·기사 계약은 발주사 생성과
 * 참여 요청·승인 API로 만든다. 이름은 가입을 마친 profile 이름을 SQL로 바꿔 구분한다.
 */
@AutoConfigureMockMvc
@DisplayName("참여 인원 API")
class ParticipantApiIntegrationTest extends IntegrationTestSupport {

    private static final AtomicLong ACCOUNT_IDS = new AtomicLong(8_000);

    private final SignupTestSupport.SignupOnce signups = new SignupTestSupport.SignupOnce();

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
    @DisplayName("총관리자는 총관리자·직원·기사를 이름 가나다순으로 역할·상태·참여일과 역할별 인원 수와 함께 받는다")
    void ownerListsParticipants() throws Exception {
        long owner = ACCOUNT_IDS.incrementAndGet();
        Company company = createCompany(owner, "참여 인원 발주사");
        long staff = ACCOUNT_IDS.incrementAndGet();
        long technician = ACCOUNT_IDS.incrementAndGet();
        long staffMembershipId = join(company, owner, staff, "STAFF");
        long technicianId = join(company, owner, technician, "TECHNICIAN");
        rename(owner, "하총괄");
        rename(staff, "김직원");
        rename(technician, "나기사");

        listParticipants(owner, company.organizationId(), null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.counts.all").value(3))
                .andExpect(jsonPath("$.result.counts.staff").value(2))
                .andExpect(jsonPath("$.result.counts.technician").value(1))
                .andExpect(jsonPath("$.result.participants.length()").value(3))
                .andExpect(jsonPath("$.result.participants[0].memberId").value(staffMembershipId))
                .andExpect(jsonPath("$.result.participants[0].accountId").value(staff))
                .andExpect(jsonPath("$.result.participants[0].name").value("김직원"))
                .andExpect(jsonPath("$.result.participants[0].role").value("STAFF"))
                .andExpect(jsonPath("$.result.participants[0].category").isEmpty())
                .andExpect(jsonPath("$.result.participants[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.result.participants[0].joinedAt").exists())
                .andExpect(jsonPath("$.result.participants[1].memberId").value(technicianId))
                .andExpect(jsonPath("$.result.participants[1].role").value("TECHNICIAN"))
                .andExpect(jsonPath("$.result.participants[2].name").value("하총괄"))
                .andExpect(jsonPath("$.result.participants[2].role").value("OWNER"));
    }

    @Test
    @DisplayName("역할 필터와 이름 검색으로 거르고, 인원 수는 검색 결과를 역할별로 센다")
    void filtersByTypeAndSearchesName() throws Exception {
        long owner = ACCOUNT_IDS.incrementAndGet();
        Company company = createCompany(owner, "검색 발주사");
        long technician = ACCOUNT_IDS.incrementAndGet();
        long otherTechnician = ACCOUNT_IDS.incrementAndGet();
        join(company, owner, technician, "TECHNICIAN");
        join(company, owner, otherTechnician, "TECHNICIAN");
        rename(owner, "홍총괄");
        rename(technician, "홍기사");
        rename(otherTechnician, "이기사");

        listParticipants(owner, company.organizationId(), "TECHNICIAN", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.counts.all").value(3))
                .andExpect(jsonPath("$.result.participants.length()").value(2))
                .andExpect(jsonPath("$.result.participants[0].name").value("이기사"))
                .andExpect(jsonPath("$.result.participants[1].name").value("홍기사"));
        listParticipants(owner, company.organizationId(), null, " 홍 ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.counts.all").value(2))
                .andExpect(jsonPath("$.result.counts.staff").value(1))
                .andExpect(jsonPath("$.result.counts.technician").value(1))
                .andExpect(jsonPath("$.result.participants[0].name").value("홍기사"))
                .andExpect(jsonPath("$.result.participants[1].name").value("홍총괄"));
    }

    @Test
    @DisplayName("직원은 403 ORGANIZATION-003, 기사·다른 발주사 총관리자는 403 ORGANIZATION-002, 목록에 없는 역할 값은 400이다")
    void refusesNonOwnersAndUnknownType() throws Exception {
        long owner = ACCOUNT_IDS.incrementAndGet();
        Company company = createCompany(owner, "권한 발주사");
        long staff = ACCOUNT_IDS.incrementAndGet();
        long technician = ACCOUNT_IDS.incrementAndGet();
        long otherOwner = ACCOUNT_IDS.incrementAndGet();
        join(company, owner, staff, "STAFF");
        join(company, owner, technician, "TECHNICIAN");
        createCompany(otherOwner, "남의 발주사");

        listParticipants(staff, company.organizationId(), null, null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORGANIZATION-003"));
        listParticipants(technician, company.organizationId(), null, null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORGANIZATION-002"));
        listParticipants(otherOwner, company.organizationId(), null, null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORGANIZATION-002"));
        listParticipants(owner, company.organizationId(), "OWNER", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-400"));
    }

    @Test
    @DisplayName("직원 소속에는 직원 유형을, 기사 계약에는 기사 유형을 지정하면 목록에 보이고, 비우면 유형 없음으로 돌아간다")
    void assignsAndUnassignsCategories() throws Exception {
        long owner = ACCOUNT_IDS.incrementAndGet();
        Company company = createCompany(owner, "유형 지정 발주사");
        long staff = ACCOUNT_IDS.incrementAndGet();
        long technician = ACCOUNT_IDS.incrementAndGet();
        long membershipId = join(company, owner, staff, "STAFF");
        long technicianId = join(company, owner, technician, "TECHNICIAN");
        long staffCategory = createCategory(owner, company.organizationId(), "staff-categories", "정규직");
        long technicianCategory = createCategory(owner, company.organizationId(), "technician-categories", "전기 기사");

        assignCategory(owner, company.organizationId(), "staff-memberships", membershipId, staffCategory)
                .andExpect(status().isNoContent());
        assignCategory(owner, company.organizationId(), "technicians", technicianId, technicianCategory)
                .andExpect(status().isNoContent());

        listParticipants(owner, company.organizationId(), "TECHNICIAN", null)
                .andExpect(
                        jsonPath("$.result.participants[0].category.categoryId").value(technicianCategory))
                .andExpect(jsonPath("$.result.participants[0].category.name").value("전기 기사"))
                .andExpect(jsonPath("$.result.participants[0].category.color").value("#3B82F6"));
        listParticipants(owner, company.organizationId(), null, null)
                .andExpect(jsonPath("$.result.participants[?(@.memberId == " + membershipId + " && @.role == 'STAFF')]"
                                + ".category.name")
                        .value("정규직"));

        assignCategory(owner, company.organizationId(), "technicians", technicianId, null)
                .andExpect(status().isNoContent());
        listParticipants(owner, company.organizationId(), "TECHNICIAN", null)
                .andExpect(jsonPath("$.result.participants[0].category").isEmpty());
    }

    @Test
    @DisplayName("삭제된 유형·다른 발주사의 유형은 404 ORGANIZATION-008, 다른 발주사의 기사 계약은 404 ORGANIZATION-020, 직원은 403이다")
    void refusesInvalidCategoryAssignments() throws Exception {
        long owner = ACCOUNT_IDS.incrementAndGet();
        Company company = createCompany(owner, "유형 거부 발주사");
        long otherOwner = ACCOUNT_IDS.incrementAndGet();
        Company otherCompany = createCompany(otherOwner, "다른 유형 발주사");
        long staff = ACCOUNT_IDS.incrementAndGet();
        long technician = ACCOUNT_IDS.incrementAndGet();
        long membershipId = join(company, owner, staff, "STAFF");
        long technicianId = join(company, owner, technician, "TECHNICIAN");
        long deleted = createCategory(owner, company.organizationId(), "technician-categories", "배관 기사");
        mockMvc.perform(delete(
                                "/api/v1/organizations/{organizationId}/technician-categories/{categoryId}",
                                company.organizationId(),
                                deleted)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(owner)))
                .andExpect(status().isNoContent());
        long foreign = createCategory(otherOwner, otherCompany.organizationId(), "technician-categories", "전기 기사");

        assignCategory(owner, company.organizationId(), "technicians", technicianId, deleted)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORGANIZATION-008"));
        assignCategory(owner, company.organizationId(), "technicians", technicianId, foreign)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORGANIZATION-008"));
        assignCategory(otherOwner, otherCompany.organizationId(), "technicians", technicianId, foreign)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORGANIZATION-020"));
        assignCategory(staff, company.organizationId(), "staff-memberships", membershipId, null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORGANIZATION-003"));
    }

    private ResultActions listParticipants(long accountId, long organizationId, String type, String name)
            throws Exception {
        MockHttpServletRequestBuilder request = get(
                        "/api/v1/organizations/{organizationId}/participants", organizationId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(accountId));
        if (type != null) {
            request.queryParam("type", type);
        }
        if (name != null) {
            request.queryParam("name", name);
        }
        return mockMvc.perform(request);
    }

    /** 참여 요청을 보내고 확정 유형으로 승인한 뒤, 생긴 직원 소속 또는 기사 계약의 식별자를 돌려준다. */
    private long join(Company company, long owner, long applicant, String type) throws Exception {
        long requestId = result(mockMvc.perform(post("/api/v1/participation-requests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(applicant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "companyCode",
                                company.code(),
                                "desiredType",
                                type,
                                "requestChannel",
                                "COMPANY_CODE")))))
                .path("requestId")
                .asLong();
        result(mockMvc.perform(post(
                        "/api/v1/organizations/{organizationId}/participation-requests/approvals",
                        company.organizationId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        Map.of("approvals", List.of(Map.of("requestId", requestId, "confirmedType", type)))))));
        String table = "STAFF".equals(type) ? "company_memberships" : "technician";
        return jdbcTemplate.queryForObject(
                "select id from " + table + " where company_id = ? and member_id = ?",
                Long.class,
                company.organizationId(),
                applicant);
    }

    private long createCategory(long owner, long organizationId, String collection, String name) throws Exception {
        return result(mockMvc.perform(post("/api/v1/organizations/{organizationId}/" + collection, organizationId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", name, "color", "#3B82F6")))))
                .path("categoryId")
                .asLong();
    }

    private ResultActions assignCategory(
            long accountId, long organizationId, String collection, long memberId, Long categoryId) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("categoryId", categoryId);
        return mockMvc.perform(put(
                        "/api/v1/organizations/{organizationId}/" + collection + "/{memberId}/category",
                        organizationId,
                        memberId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(accountId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private void rename(long accountId, String name) {
        jdbcTemplate.update("update profiles set name = ? where account_id = ?", name, accountId);
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
