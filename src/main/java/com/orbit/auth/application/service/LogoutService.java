package com.orbit.auth.application.service;

import java.time.Clock;
import java.time.Duration;

import org.springframework.stereotype.Service;

import com.orbit.auth.application.port.in.command.LogoutUseCase;
import com.orbit.auth.application.port.in.command.dto.LogoutCommand;
import com.orbit.auth.application.port.out.RefreshSessionRepository;
import com.orbit.auth.application.port.out.RevokedAccessTokenPort;
import com.orbit.auth.domain.AccountId;
import com.orbit.auth.domain.RefreshSessionId;

/**
 * 로그아웃한 Access Token을 만료까지 폐기 목록에 두고, 그 토큰을 발급한 기기 세션을 지워 이 기기의 refresh token을 끝낸다. 같은 계정의 다른 기기 세션은
 * 남긴다. 메서드 트랜잭션 없이 폐기 저장소와 세션 저장소가 각자 확정한다.
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
    public void logout(LogoutCommand command) {
        Duration remaining = Duration.between(clock.instant(), command.tokenExpiresAt());
        revokedTokens.revoke(command.tokenId(), remaining.isNegative() ? Duration.ZERO : remaining);
        if (command.sessionId() != null) {
            sessions.deleteOwnedBy(new RefreshSessionId(command.sessionId()), new AccountId(command.accountId()));
        }
    }
}
