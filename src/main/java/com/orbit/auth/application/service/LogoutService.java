package com.orbit.auth.application.service;

import java.time.Clock;
import java.time.Duration;

import org.springframework.stereotype.Service;

import com.orbit.auth.application.port.in.command.LogoutUseCase;
import com.orbit.auth.application.port.in.command.dto.LogoutCommand;
import com.orbit.auth.application.port.out.RevokedAccessTokenPort;

/** 로그아웃한 Access Token을 만료까지 폐기 목록에 둔다. DB 트랜잭션 없이 폐기 저장소만 사용한다. */
@Service
public class LogoutService implements LogoutUseCase {

    private final RevokedAccessTokenPort revokedTokens;
    private final Clock clock;

    public LogoutService(RevokedAccessTokenPort revokedTokens, Clock clock) {
        this.revokedTokens = revokedTokens;
        this.clock = clock;
    }

    @Override
    public void logout(LogoutCommand command) {
        Duration remaining = Duration.between(clock.instant(), command.tokenExpiresAt());
        revokedTokens.revoke(command.tokenId(), remaining.isNegative() ? Duration.ZERO : remaining);
    }
}
