package com.orbit.auth.adapter.in.web.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import com.orbit.auth.application.port.in.query.AuthenticateAccessTokenUseCase;
import com.orbit.auth.application.port.in.query.dto.AccessTokenInfo;
import com.orbit.auth.application.port.in.query.dto.AuthenticateAccessTokenQuery;
import com.orbit.shared.error.CommonErrorCode;
import com.orbit.shared.security.ApiErrorResponseWriter;

@ExtendWith(MockitoExtension.class)
@DisplayName("Access Token 인증 필터")
class AccessTokenAuthenticationFilterTest {

    private static final Instant EXPIRES_AT = Instant.parse("2026-09-27T02:00:00Z");

    @Mock
    private AuthenticateAccessTokenUseCase useCase;

    @Mock
    private ApiErrorResponseWriter responseWriter;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Bearer 헤더가 없으면 인증 없이 다음 필터로 넘긴다")
    void passesThroughWithoutBearerHeader() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        filter().doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(useCase, never()).authenticate(any());
    }

    @Test
    @DisplayName("유효한 토큰이면 계정 principal로 인증 컨텍스트를 채운다")
    void authenticatesValidToken() throws Exception {
        when(useCase.authenticate(new AuthenticateAccessTokenQuery("token")))
                .thenReturn(Optional.of(new AccessTokenInfo(7L, "jti", EXPIRES_AT)));
        MockFilterChain chain = new MockFilterChain();

        filter().doFilter(request("Bearer token"), new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .isInstanceOfSatisfying(AccessTokenAuthentication.class, authentication -> {
                    assertThat(authentication.isAuthenticated()).isTrue();
                    assertThat(authentication.getPrincipal())
                            .isEqualTo(new AuthenticatedAccount(7L, "jti", EXPIRES_AT));
                });
    }

    @Test
    @DisplayName("폐기·만료·위조된 토큰이면 404 응답으로 끝내고 다음 필터로 넘기지 않는다")
    void endsRequestWithNotFoundForInvalidToken() throws Exception {
        when(useCase.authenticate(new AuthenticateAccessTokenQuery("stale"))).thenReturn(Optional.empty());
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request("bearer stale"), response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(responseWriter).write(response, CommonErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("카카오 로그인 경로는 토큰이 있어도 검사하지 않고 넘긴다")
    void skipsLoginPathsEvenWithToken() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletRequest request = request("Bearer stale");
        request.setRequestURI("/api/v1/auth/kakao/nonces");
        request.setMethod("POST");

        filter().doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
        verify(useCase, never()).authenticate(any());
    }

    @Test
    @DisplayName("Apple 로그인 경로도 토큰이 있어도 검사하지 않고 넘긴다")
    void skipsAppleLoginPathsEvenWithToken() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletRequest request = request("Bearer stale");
        request.setRequestURI("/api/v1/auth/apple/login");
        request.setMethod("POST");

        filter().doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
        verify(useCase, never()).authenticate(any());
    }

    @Test
    @DisplayName("인증 처리 중 실패하면 공통 실패 봉투의 500으로 끝낸다")
    void endsRequestWithServerErrorEnvelopeOnFailure() throws Exception {
        when(useCase.authenticate(any())).thenThrow(new IllegalStateException("redis down"));
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request("Bearer token"), response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(responseWriter).write(response, CommonErrorCode.INTERNAL_SERVER_ERROR);
    }

    @Test
    @DisplayName("Authentication 이름은 계정 식별자이며 토큰 식별자를 드러내지 않는다")
    void exposesAccountIdAsName() {
        AccessTokenAuthentication authentication =
                new AccessTokenAuthentication(new AuthenticatedAccount(7L, "secret-jti", EXPIRES_AT));

        assertThat(authentication.getName()).isEqualTo("7");
    }

    private AccessTokenAuthenticationFilter filter() {
        return new AccessTokenAuthenticationFilter(useCase, responseWriter);
    }

    private static MockHttpServletRequest request(String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", authorization);
        return request;
    }
}
