package com.orbit.auth.application.service;

import java.time.Clock;
import java.time.Duration;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.auth.application.port.in.command.LogoutUseCase;
import com.orbit.auth.application.port.in.command.dto.LogoutCommand;
import com.orbit.auth.application.port.out.RefreshSessionRepository;
import com.orbit.auth.application.port.out.RevokedAccessTokenPort;
import com.orbit.auth.domain.AccountId;
import com.orbit.auth.domain.RefreshSessionId;
import com.orbit.auth.domain.RefreshTokenHash;

/**
 * 로그아웃한 Access Token을 만료까지 폐기 목록에 두고, 그 토큰을 발급한 기기 세션을 지워 이 기기의 refresh token을 끝낸다. Access Token이 만료된
 * 기기는 함께 보낸 refresh token으로 그 세션을 끝낸다. 같은 계정의 다른 기기 세션은 남긴다. 세션을 먼저 지우고 폐기 목록은 마지막에 기록해, 폐기 기록이
 * 실패하면 세션 삭제도 롤백되어 같은 토큰으로 다시 로그아웃할 수 있다.
 */
@Service
public class LogoutService implements LogoutUseCase {

    private final RevokedAccessTokenPort revokedTokens;
    private final RefreshSessionRepository sessions;
    private final Clock clock;

    public LogoutService(RevokedAccessTokenPort revokedTokens, RefreshSessionRepository sessions, Clock clock) {
        this.revokedTokens = revokedTokens;
        this.sessions = sessions;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void logout(LogoutCommand command) {
        if (command.sessionId() != null) {
            sessions.deleteOwnedBy(new RefreshSessionId(command.sessionId()), new AccountId(command.accountId()));
        }
        RefreshTokenValue.parse(command.refreshToken())
                .ifPresent(token -> sessions.deleteByToken(token.sessionId(), RefreshTokenHash.of(token.secret())));
        if (command.tokenId() != null) {
            Duration remaining = Duration.between(clock.instant(), command.tokenExpiresAt());
            revokedTokens.revoke(command.tokenId(), remaining.isNegative() ? Duration.ZERO : remaining);
        }
    }
}
