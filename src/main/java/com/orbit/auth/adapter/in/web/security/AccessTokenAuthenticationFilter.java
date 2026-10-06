package com.orbit.auth.adapter.in.web.security;

import java.io.IOException;
import java.util.Optional;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import com.orbit.auth.application.port.in.query.AuthenticateAccessTokenUseCase;
import com.orbit.auth.application.port.in.query.dto.AccessTokenInfo;
import com.orbit.auth.application.port.in.query.dto.AuthenticateAccessTokenQuery;
import com.orbit.shared.error.CommonErrorCode;
import com.orbit.shared.security.ApiErrorResponseWriter;

import lombok.extern.slf4j.Slf4j;

/**
 * Bearer 토큰을 인증한다. 토큰이 없으면 익명으로 넘겨 인증 필요 자원은 401을 받게 하고, 토큰이 있는데 폐기·만료·위조됐으면 자원 존재를 드러내지 않도록
 * 실제 404와 같은 응답으로 끝낸다. 카카오·Apple 로그인 경로는 만료·로그아웃된 토큰을 아직 들고 있는 클라이언트가 다시 로그인할 수 있도록 검사하지 않는다. Bean으로
 * 등록하지 않고 {@link AuthSecurityCustomizer}가 Security 체인에만 끼워 서블릿 필터로 중복 등록되지 않게 한다.
 */
@Slf4j
class AccessTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final RequestMatcher LOGIN_PATHS = new OrRequestMatcher(
            PathPatternRequestMatcher.withDefaults().matcher(AuthSecurityCustomizer.KAKAO_LOGIN_PATHS),
            PathPatternRequestMatcher.withDefaults().matcher(AuthSecurityCustomizer.APPLE_LOGIN_PATHS));

    private final AuthenticateAccessTokenUseCase useCase;
    private final ApiErrorResponseWriter responseWriter;

    AccessTokenAuthenticationFilter(AuthenticateAccessTokenUseCase useCase, ApiErrorResponseWriter responseWriter) {
        this.useCase = useCase;
        this.responseWriter = responseWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return LOGIN_PATHS.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<String> token = bearerToken(request);
        if (token.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }
        Optional<AccessTokenInfo> info;
        try {
            info = useCase.authenticate(new AuthenticateAccessTokenQuery(token.get()));
        } catch (RuntimeException exception) {
            // ExceptionTranslationFilter보다 앞이라 전역 처리기가 받지 못하므로 같은 실패 봉투로 직접 응답한다.
            log.error("Access token authentication failed", exception);
            SecurityContextHolder.clearContext();
            responseWriter.write(response, CommonErrorCode.INTERNAL_SERVER_ERROR);
            return;
        }
        if (info.isEmpty()) {
            SecurityContextHolder.clearContext();
            responseWriter.write(response, CommonErrorCode.NOT_FOUND);
            return;
        }
        AccessTokenAuthentication authentication = new AccessTokenAuthentication(new AuthenticatedAccount(
                info.get().accountId(), info.get().tokenId(), info.get().expiresAt()));
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        chain.doFilter(request, response);
    }

    private static Optional<String> bearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return Optional.empty();
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? Optional.empty() : Optional.of(token);
    }
}
