package com.orbit.realtime.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.orbit.support.IntegrationTestSupport;
import com.orbit.support.SignupTestSupport;

import tools.jackson.databind.ObjectMapper;

/**
 * 실제 Access Token 인증과 organization 소속 조회를 거쳐 SSE 연결을 연다. 발주사는 API로 만들고(생성자가 총관리자), 기사 계약은 테이블에 직접 넣는다. 연결은
 * 인스턴스 메모리에 남으므로 계정 식별자를 테스트마다 달리해 계정 상한이 다른 테스트와 섞이지 않게 한다.
 */
@DisplayName("실시간 갱신 연결 API")
class RealtimeStreamApiIntegrationTest extends IntegrationTestSupport {

    private static final AtomicLong ACCOUNT_IDS = new AtomicLong(90_000);
    private static final String PATH = "/api/v1/organizations/{organizationId}/events";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final SignupTestSupport.SignupOnce signups = new SignupTestSupport.SignupOnce();

    @Value("${app.auth.jwt.secret}")
    private String jwtSecret;

    @Value("${app.auth.jwt.issuer}")
    private String jwtIssuer;

    @Test
    @DisplayName("발주사 직원이 열면 이벤트 스트림으로 connected를 받고 앞단이 모아 두지 않는 응답 헤더를 받는다")
    void opensStreamForStaff() throws Exception {
        long owner = ACCOUNT_IDS.incrementAndGet();
        long organizationId = organization(owner);

        MvcResult result = mockMvc.perform(get(PATH, organizationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .header(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE)
                        .header("X-Orbit-Client", "desktop-web"))
                .andExpect(request().asyncStarted())
                .andReturn();

        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() ->
                        assertThat(result.getResponse().getContentAsString()).contains("event:connected\ndata:{}\n\n"));
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentType()).startsWith(MediaType.TEXT_EVENT_STREAM_VALUE);
        assertThat(result.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-cache");
        assertThat(result.getResponse().getHeader("X-Accel-Buffering")).isEqualTo("no");
    }

    @Test
    @DisplayName("기사·비소속 계정은 이벤트 스트림만 받겠다고 해도 JSON 403(REALTIME-001)을 받는다")
    void rejectsNonStaffWithJsonError() throws Exception {
        long owner = ACCOUNT_IDS.incrementAndGet();
        long technician = ACCOUNT_IDS.incrementAndGet();
        long outsider = ACCOUNT_IDS.incrementAndGet();
        long organizationId = organization(owner);
        technicianContract(organizationId, technician);

        for (long accountId : List.of(technician, outsider)) {
            mockMvc.perform(get(PATH, organizationId)
                            .header(HttpHeaders.AUTHORIZATION, bearer(accountId))
                            .header(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE))
                    .andExpect(status().isForbidden())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.code").value("REALTIME-001"));
        }
    }

    @Test
    @DisplayName("다른 출처의 화면도 다시 연결할 때를 읽도록 Retry-After를 노출한다")
    void exposesRetryAfterToBrowsers() throws Exception {
        long outsider = ACCOUNT_IDS.incrementAndGet();

        mockMvc.perform(get(PATH, 1)
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header(HttpHeaders.AUTHORIZATION, bearer(outsider))
                        .header(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE))
                .andExpect(status().isForbidden())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, containsString("Retry-After")));
    }

    @Test
    @DisplayName("발주사 식별자가 양수가 아니면 COMMON-400, 토큰이 없으면 401이다")
    void rejectsInvalidOrganizationAndAnonymousRequests() throws Exception {
        long owner = ACCOUNT_IDS.incrementAndGet();

        mockMvc.perform(get(PATH, 0)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .header(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-400"));
        mockMvc.perform(get(PATH, 1).header(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("같은 계정이 상한을 넘겨 열면 가장 오래된 연결이 closed(replaced)를 받고, 끝난 연결의 비동기 재디스패치는 인증 없이 통과한다")
    void replacesOldestConnectionAndCompletesThroughAsyncDispatch() throws Exception {
        long owner = ACCOUNT_IDS.incrementAndGet();
        long organizationId = organization(owner);
        String bearer = bearer(owner);
        List<MvcResult> opened = new ArrayList<>();

        for (int i = 0; i < 6; i++) {
            opened.add(mockMvc.perform(get(PATH, organizationId)
                            .header(HttpHeaders.AUTHORIZATION, bearer)
                            .header(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE))
                    .andExpect(request().asyncStarted())
                    .andReturn());
        }

        MvcResult oldest = opened.getFirst();
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(oldest.getResponse().getContentAsString())
                        .contains("event:closed\ndata:{\"reason\":\"replaced\"}\n\n"));
        // 닫힌 연결의 결과가 정해질 때까지 기다린 뒤 컨테이너처럼 비동기로 다시 디스패치한다.
        mockMvc.perform(asyncDispatch(oldest)).andExpect(status().isOk());
        for (MvcResult open : opened.subList(1, opened.size())) {
            assertThat(open.getResponse().getContentAsString()).doesNotContain("event:closed");
        }
    }

    private long organization(long owner) throws Exception {
        String body = mockMvc.perform(post("/api/v1/organizations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "오르빗 설비", "industry", "HVAC"))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).path("result").path("organizationId").asLong();
    }

    private void technicianContract(long organizationId, long accountId) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(
                "insert into technician"
                        + " (company_id, member_id, status, status_changed_at, joined_at, updated_at, created_at)"
                        + " values (?, ?, 'ACTIVE', ?, ?, ?, current_timestamp)",
                organizationId,
                accountId,
                now,
                now,
                now);
    }

    private String bearer(long accountId) throws Exception {
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
        return "Bearer " + accessToken;
    }
}
