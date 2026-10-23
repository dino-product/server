package com.orbit.auth.application.service;

import java.time.Clock;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.auth.application.error.AuthErrorCode;
import com.orbit.auth.application.port.in.command.RefreshTokensUseCase;
import com.orbit.auth.application.port.in.command.dto.RefreshTokensCommand;
import com.orbit.auth.application.port.in.command.dto.RefreshedTokensInfo;
import com.orbit.auth.application.port.out.AccountRepository;
import com.orbit.auth.application.port.out.RefreshSessionRepository;
import com.orbit.auth.application.port.out.RefreshTokenSecretPort;
import com.orbit.auth.domain.RefreshOutcome;
import com.orbit.auth.domain.RefreshSession;
import com.orbit.auth.domain.RefreshTokenHash;
import com.orbit.shared.error.BusinessException;

import lombok.extern.slf4j.Slf4j;

/**
 * refresh token으로 같은 기기 세션의 토큰을 다시 발급한다. 세션 행을 잠가 같은 토큰의 동시 재발급을 줄 세우고, 다음 비밀값은 제출된 비밀값에서 결정적으로
 * 파생하므로 회전 직후 다시 온 직전 토큰에도 같은 다음 토큰을 돌려준다. 재사용·만료로 세션을 지운 뒤 거절하므로 그 삭제는 거절 예외로 롤백하지 않는다. 실패
 * 이유는 응답에서 구분하지 않는다.
 *
 * <p>계정 폐쇄는 같은 트랜잭션에서 세션을 지우지만, 폐쇄 표시가 남기 전에 Apple 웹 교환이 새 세션을 만드는 틈이 있다. 폐쇄 표시는 Access Token 수명만큼만
 * 남으므로, 그 세션으로 표시가 사라진 뒤 토큰을 받지 못하게 재발급마다 계정이 닫혔는지 저장소에서 확인한다.
 */
@Slf4j
@Service
public class RefreshTokensService implements RefreshTokensUseCase {

    private final RefreshSessionRepository sessions;
    private final AccountRepository accounts;
    private final RefreshTokenSecretPort secrets;
    private final SessionTokenIssuer tokens;
    private final Clock clock;

    public RefreshTokensService(
            RefreshSessionRepository sessions,
            AccountRepository accounts,
            RefreshTokenSecretPort secrets,
            SessionTokenIssuer tokens,
            Clock clock) {
        this.sessions = sessions;
        this.accounts = accounts;
        this.secrets = secrets;
        this.tokens = tokens;
        this.clock = clock;
    }

    @Override
    @Transactional(noRollbackFor = BusinessException.class)
    public RefreshedTokensInfo refresh(RefreshTokensCommand command) {
        RefreshTokenValue presented =
                RefreshTokenValue.parse(command.refreshToken()).orElseThrow(RefreshTokensService::rejected);
        RefreshSession session =
                sessions.findByIdForUpdate(presented.sessionId()).orElseThrow(RefreshTokensService::rejected);
        if (accounts.findById(session.accountId())
                .filter(account -> !account.isClosed())
                .isEmpty()) {
            sessions.delete(session.id());
            throw rejected();
        }
        RefreshTokenValue next = new RefreshTokenValue(session.id(), secrets.next(session.id(), presented.secret()));
        RefreshOutcome outcome = session.refresh(
                RefreshTokenHash.of(presented.secret()),
                RefreshTokenHash.of(next.secret()),
                clock.instant(),
                secrets.idleLifetime());
        switch (outcome) {
            case ROTATED -> sessions.update(session);
            case REPLAYED -> {
                // 이미 회전한 다음 토큰을 다시 보내므로 저장할 것이 없다.
            }
            case STALE -> throw rejected();
            case REUSED -> {
                log.warn(
                        "Refresh token reuse detected; session revoked: accountId={}, sessionId={}",
                        session.accountId().value(),
                        session.id().value());
                sessions.delete(session.id());
                throw rejected();
            }
            case EXPIRED -> {
                sessions.delete(session.id());
                throw rejected();
            }
            default -> throw new IllegalStateException("Unexpected refresh outcome: " + outcome);
        }
        IssuedSessionTokens issued = tokens.issue(session, next);
        return new RefreshedTokensInfo(
                issued.accessToken(),
                issued.accessTokenExpiresAt(),
                issued.refreshToken(),
                issued.refreshTokenExpiresAt());
    }

    private static BusinessException rejected() {
        return new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
    }
}
