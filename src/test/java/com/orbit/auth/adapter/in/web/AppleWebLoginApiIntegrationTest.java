package com.orbit.auth.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponentsBuilder;

import com.orbit.support.AppleAuthStub;
import com.orbit.support.IntegrationTestSupport;

/** 웹·Android Apple 로그인. Apple 엔드포인트는 로컬 스텁이고 state·Redis·PostgreSQL·토큰 교환 경로는 실제 구현을 지난다. 실제 Apple E2E는 아니다. */
@AutoConfigureMockMvc
@DisplayName("Apple 웹·Android 로그인 API")
class AppleWebLoginApiIntegrationTest extends IntegrationTestSupport {

    private static final AppleAuthStub APPLE = AppleAuthStub.start();

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void appleEndpoints(DynamicPropertyRegistry registry) {
        registry.add("app.auth.apple.jwk-set-uri", APPLE::jwkSetUri);
        registry.add("app.auth.apple.token-api.token-uri", APPLE::tokenUri);
        registry.add("app.auth.apple.token-api.revoke-uri", APPLE::revokeUri);
        registry.add("app.auth.apple.client-secret.private-key", APPLE::clientSecretPrivateKeyPem);
    }

    @AfterAll
    static void stopStub() {
        APPLE.close();
    }

    @Test
    @DisplayName("시작하면 Apple 인가 주소로 302 보내고 콜백 경로에만 쓰는 연결 쿠키를 남긴다")
    void redirectsToAppleWithBrowserBindingCookie() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/apple/authorize").param("client", "web"))
                .andExpect(status().isFound())
                .andReturn();

        URI location = URI.create(result.getResponse().getHeader(HttpHeaders.LOCATION));
        var query = UriComponentsBuilder.fromUri(location).build().getQueryParams();
        assertThat(location.getHost()).isEqualTo("appleid.apple.com");
        assertThat(query.getFirst("client_id")).isEqualTo(AppleAuthStub.SERVICES_ID);
        assertThat(query.getFirst("response_mode")).isEqualTo("form_post");
        assertThat(query.getFirst("state")).isNotBlank();
        assertThat(query.getFirst("nonce")).matches("[0-9a-f]{64}");
        String cookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(cookie)
                .startsWith(AppleWebLoginController.BROWSER_BINDING_COOKIE + "=")
                .contains("Path=/api/v1/auth/apple", "HttpOnly", "Secure", "SameSite=None", "Max-Age=600");
    }

    @Test
    @DisplayName("등록하지 않은 클라이언트로는 시작할 수 없다")
    void rejectsUnregisteredClient() throws Exception {
        mockMvc.perform(get("/api/v1/auth/apple/authorize").param("client", "https://evil.example"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-400"));
        mockMvc.perform(get("/api/v1/auth/apple/authorize"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-400"));
    }

    @Test
    @DisplayName("무효한 토큰을 붙인 채로도 시작할 수 있다")
    void startsWithStaleTokenAttached() throws Exception {
        mockMvc.perform(get("/api/v1/auth/apple/authorize")
                        .param("client", "android")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer stale"))
                .andExpect(status().isFound());
    }
}
