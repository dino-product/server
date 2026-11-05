package com.orbit.auth.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.orbit.auth.application.port.out.AccessTokenPort;
import com.orbit.auth.application.port.out.IssuedAccessToken;
import com.orbit.auth.application.port.out.RefreshSessionRepository;
import com.orbit.auth.application.port.out.RefreshTokenSecretPort;
import com.orbit.auth.domain.AccountId;
import com.orbit.auth.domain.RefreshSession;
import com.orbit.auth.domain.RefreshSessionId;
import com.orbit.auth.domain.RefreshTokenHash;

/**
 * 로그인으로 기기 세션을 시작하고 그 세션의 Access Token과 refresh token을 발급한다. 같은 계정의 만료된 세션은 이때 함께 지운다. 저장은 호출자 트랜잭션이
 * 있으면 거기에 참여하고, 외부 HTTP를 기다려 트랜잭션을 두지 않는 로그인에서는 저장소가 각자 확정한다.
 */
@Component
class SessionTokenIssuer {

    private final AccessTokenPort accessTokens;
    private final RefreshSessionRepository sessions;
    private final RefreshTokenSecretPort secrets;
    private final Clock clock;

    SessionTokenIssuer(
            AccessTokenPort accessTokens,
            RefreshSessionRepository sessions,
            RefreshTokenSecretPort secrets,
            Clock clock) {
        this.accessTokens = accessTokens;
        this.sessions = sessions;
        this.secrets = secrets;
        this.clock = clock;
    }

    IssuedSessionTokens start(AccountId accountId) {
        Instant now = clock.instant();
        sessions.deleteExpiredByAccount(accountId, now);
        RefreshTokenValue refreshToken =
                new RefreshTokenValue(new RefreshSessionId(UUID.randomUUID().toString()), secrets.newSecret());
        RefreshSession session = RefreshSession.start(
                refreshToken.sessionId(),
                accountId,
                RefreshTokenHash.of(refreshToken.secret()),
                now,
                secrets.idleLifetime());
        sessions.saveNew(session);
        return issue(session, refreshToken);
    }

    /** 저장한 세션의 현재 refresh token에 새 Access Token을 묶는다. */
    IssuedSessionTokens issue(RefreshSession session, RefreshTokenValue refreshToken) {
        IssuedAccessToken accessToken = accessTokens.issue(session.accountId(), session.id());
        return new IssuedSessionTokens(
                accessToken.value(), accessToken.token().expiresAt(), refreshToken.format(), session.expiresAt());
    }
}
