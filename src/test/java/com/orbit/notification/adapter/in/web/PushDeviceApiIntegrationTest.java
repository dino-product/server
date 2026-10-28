package com.orbit.notification.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.orbit.notification.domain.PushDevice;
import com.orbit.support.IntegrationTestSupport;
import com.orbit.support.SignupTestSupport;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 요청자는 실제 Access Token 인증을 거친 auth의 {@code AccountPrincipal}에서 얻는다. 계정 식별자와 토큰을 테스트마다 달리해 같은 DB의 다른 테스트 행과 섞이지
 * 않게 한다.
 */
@DisplayName("앱 푸시 기기 API")
class PushDeviceApiIntegrationTest extends IntegrationTestSupport {

    private static final AtomicLong ACCOUNT_IDS = new AtomicLong(32_000);

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
    @DisplayName("기기를 등록하면 식별자를 주고 같은 토큰을 다시 등록하면 같은 식별자를 준다")
    void registersDeviceAndKeepsIdForSameToken() throws Exception {
        long accountId = ACCOUNT_IDS.incrementAndGet();
        String token = newToken();

        long first = register(accountId, "ANDROID", token);
        long second = register(accountId, "ANDROID", token);

        assertThat(second).isEqualTo(first);
        assertThat(ownerOf(token)).isEqualTo(accountId);
    }

    @Test
    @DisplayName("다른 계정이 등록했던 토큰을 등록하면 기기가 요청자 계정으로 넘어간다")
    void movesDeviceToRequesterAccount() throws Exception {
        long previousAccountId = ACCOUNT_IDS.incrementAndGet();
        long accountId = ACCOUNT_IDS.incrementAndGet();
        String token = newToken();

        long previous = register(previousAccountId, "IOS", token);
        long moved = register(accountId, "IOS", token);

        assertThat(moved).isEqualTo(previous);
        assertThat(ownerOf(token)).isEqualTo(accountId);
    }

    @Test
    @DisplayName("플랫폼이 빠지거나 토큰이 비었거나 너무 길면 COMMON-400이고 응답에 토큰을 되돌려 주지 않는다")
    void rejectsInvalidRegistrationWithoutEchoingToken() throws Exception {
        long accountId = ACCOUNT_IDS.incrementAndGet();
        String tooLong = "SECRET" + "a".repeat(PushDevice.MAX_TOKEN_LENGTH);

        String body = mockMvc.perform(authorized(post("/api/v1/push-devices"), accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"platform\": \"IOS\", \"token\": \"" + tooLong + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-400"))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).doesNotContain("SECRET");

        mockMvc.perform(authorized(post("/api/v1/push-devices"), accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"" + newToken() + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-400"));
        mockMvc.perform(authorized(post("/api/v1/push-devices"), accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"platform\": \"ANDROID\", \"token\": \" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-400"));
        mockMvc.perform(authorized(post("/api/v1/push-devices"), accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"platform\": \"WINDOWS\", \"token\": \"" + newToken() + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-400"));
    }

    @Test
    @DisplayName("인증하지 않은 요청은 401로 거부한다")
    void rejectsUnauthenticatedRequest() throws Exception {
        mockMvc.perform(post("/api/v1/push-devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"platform\": \"IOS\", \"token\": \"" + newToken() + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON-401"));
    }

    private long register(long accountId, String platform, String token) throws Exception {
        String body = mockMvc.perform(authorized(post("/api/v1/push-devices"), accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"platform\": \"" + platform + "\", \"token\": \"" + token + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        JsonNode result = objectMapper.readTree(body).get("result");
        assertThat(result.get("pushDeviceId").isIntegralNumber()).isTrue();
        assertThat(body).doesNotContain(token);
        return result.get("pushDeviceId").asLong();
    }

    private long ownerOf(String token) {
        return jdbcTemplate.queryForObject("select member_id from push_devices where token = ?", Long.class, token);
    }

    private static String newToken() {
        return "fcm-" + UUID.randomUUID();
    }

    private MockHttpServletRequestBuilder authorized(MockHttpServletRequestBuilder request, long accountId)
            throws Exception {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(accountId));
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
}
