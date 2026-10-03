package com.orbit.auth.application.service;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.orbit.auth.application.port.in.query.AuthenticateAccessTokenUseCase;
import com.orbit.auth.application.port.in.query.dto.AccessTokenInfo;
import com.orbit.auth.application.port.in.query.dto.AuthenticateAccessTokenQuery;
import com.orbit.auth.application.port.out.AccessTokenPort;
import com.orbit.auth.application.port.out.RevokedAccessTokenPort;

/** 매 요청의 Access Token을 검증하고 폐기 목록과 대조한다. DB 트랜잭션 없이 토큰 검증기와 폐기 저장소만 사용한다. */
@Service
public class AuthenticateAccessTokenService implements AuthenticateAccessTokenUseCase {

    private final AccessTokenPort accessTokens;
    private final RevokedAccessTokenPort revokedTokens;

    public AuthenticateAccessTokenService(AccessTokenPort accessTokens, RevokedAccessTokenPort revokedTokens) {
        this.accessTokens = accessTokens;
        this.revokedTokens = revokedTokens;
    }

    @Override
    public Optional<AccessTokenInfo> authenticate(AuthenticateAccessTokenQuery query) {
        return accessTokens
                .parse(query.accessToken())
                .filter(token -> !revokedTokens.isRevoked(token.tokenId()))
                .map(token -> new AccessTokenInfo(token.accountId().value(), token.tokenId(), token.expiresAt()));
    }
}
