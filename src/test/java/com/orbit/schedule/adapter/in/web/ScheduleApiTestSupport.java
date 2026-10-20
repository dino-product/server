package com.orbit.schedule.adapter.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.orbit.schedule.application.port.out.LoadStaffProfilesPort;
import com.orbit.schedule.application.port.out.LoadTechnicianProfilesPort;
import com.orbit.schedule.application.port.out.LoadWorkTypeLabelsPort;
import com.orbit.schedule.application.port.out.TechnicianContractPort;
import com.orbit.schedule.application.port.out.WorkRepository;
import com.orbit.schedule.domain.Work;
import com.orbit.support.IntegrationTestSupport;

import tools.jackson.databind.ObjectMapper;

/**
 * schedule HTTP API 통합 테스트의 공용 준비물. 발주사는 API로 만들고(생성자가 총관리자), 다른 직원 소속·기사 계약은 참여 승인 기능이 아직 없어 테이블에 직접
 * 넣는다. 다른 API를 검증할 작업은 원하는 상태로 바로 맞추려고 저장소로 준비한다. 계정 식별자는 테스트 클래스 사이에서도 겹치지 않게 하나의 카운터로 만든다.
 */
abstract class ScheduleApiTestSupport extends IntegrationTestSupport {

    private static final AtomicLong ACCOUNT_IDS = new AtomicLong(70_000);

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected WorkRepository workRepository;

    // 대체하는 출력 포트. 기사 계약 확인은 테스트가 기사 계약 행을 만들지 않아 대체하며 넣지 않은 조합은 활성 기사 계약이 아니다. 표시 정보는
    // organization·profile 계약 전이라 대체한다. 모든 API 테스트가 같은 대체 Bean을 두어 Spring 컨텍스트를 함께 쓴다.
    @MockitoBean
    protected TechnicianContractPort technicianContractPort;

    @MockitoBean
    protected LoadTechnicianProfilesPort technicianProfiles;

    @MockitoBean
    protected LoadStaffProfilesPort staffProfiles;

    @MockitoBean
    protected LoadWorkTypeLabelsPort workTypeLabels;

    @Value("${app.auth.jwt.secret}")
    private String jwtSecret;

    @Value("${app.auth.jwt.issuer}")
    private String jwtIssuer;

    /** 발주사와 그 총관리자. 총관리자의 직원 소속은 작업 등록자로도 쓴다. */
    protected record Organization(long id, long owner, long ownerMembership) {}

    protected Organization organization() throws Exception {
        long owner = account();
        String body = mockMvc.perform(post("/api/v1/organizations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "오르빗 설비", "industry", "HVAC"))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long organizationId = objectMapper
                .readTree(body)
                .path("result")
                .path("organizationId")
                .asLong();
        Long membership = jdbcTemplate.queryForObject(
                "select id from company_memberships where company_id = ? and member_id = ?",
                Long.class,
                organizationId,
                owner);
        return new Organization(organizationId, owner, membership);
    }

    protected long save(Work work) {
        return workRepository.save(work).id().orElseThrow().value();
    }

    protected void staffMembership(long organizationId, long accountId) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(
                "insert into company_memberships"
                        + " (company_id, member_id, is_owner, status,"
                        + " joined_at, status_changed_at, created_at, updated_at)"
                        + " values (?, ?, false, 'ACTIVE', ?, ?, ?, ?)",
                organizationId,
                accountId,
                now,
                now,
                now,
                now);
    }

    protected void technicianContract(long organizationId, long accountId) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(
                "insert into technician (company_id, member_id, status, status_changed_at, created_at, updated_at)"
                        + " values (?, ?, 'ACTIVE', ?, ?, ?)",
                organizationId,
                accountId,
                now,
                now,
                now);
    }

    protected static long account() {
        return ACCOUNT_IDS.incrementAndGet();
    }

    protected String bearer(long accountId) throws Exception {
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
        return "Bearer " + jwt.serialize();
    }
}
