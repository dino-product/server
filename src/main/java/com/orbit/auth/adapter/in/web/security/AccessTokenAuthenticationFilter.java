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
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.orbit.auth.application.port.in.query.AuthenticateAccessTokenUseCase;
import com.orbit.auth.application.port.in.query.dto.AccessTokenInfo;
import com.orbit.auth.application.port.in.query.dto.AuthenticateAccessTokenQuery;
import com.orbit.shared.error.CommonErrorCode;
import com.orbit.shared.security.ApiErrorResponseWriter;

/**
 * Bearer 토큰을 인증한다. 토큰이 없으면 익명으로 넘겨 인증 필요 자원은 401을 받게 하고, 토큰이 있는데 폐기·만료·위조됐으면 자원 존재를 드러내지 않도록
 * 어느 경로든 실제 404와 같은 응답으로 끝낸다.
 */
@Component
class AccessTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthenticateAccessTokenUseCase useCase;
    private final ApiErrorResponseWriter responseWriter;

    AccessTokenAuthenticationFilter(AuthenticateAccessTokenUseCase useCase, ApiErrorResponseWriter responseWriter) {
        this.useCase = useCase;
        this.responseWriter = responseWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<String> token = bearerToken(request);
        if (token.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }
        Optional<AccessTokenInfo> info = useCase.authenticate(new AuthenticateAccessTokenQuery(token.get()));
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
