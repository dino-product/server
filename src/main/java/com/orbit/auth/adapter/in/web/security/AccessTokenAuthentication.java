package com.orbit.auth.adapter.in.web.security;

import java.util.List;

import org.springframework.security.authentication.AbstractAuthenticationToken;

/** 검증을 통과한 Access Token을 나타내는 Authentication. 권한은 아직 조직 소속을 연결하지 않았으므로 비어 있다. */
final class AccessTokenAuthentication extends AbstractAuthenticationToken {

    private final AuthenticatedAccount principal;

    AccessTokenAuthentication(AuthenticatedAccount principal) {
        super(List.of());
        this.principal = principal;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public AuthenticatedAccount getPrincipal() {
        return principal;
    }

    /** 접근 로그·remoteUser에는 계정 식별자만 남기고 폐기 핸들인 토큰 식별자는 노출하지 않는다. */
    @Override
    public String getName() {
        return Long.toString(principal.accountId());
    }
}
