package com.orbit.auth.application.service;

import java.time.Clock;

import com.orbit.auth.application.port.out.AppleRefreshToken;
import com.orbit.auth.application.port.out.AppleRefreshTokenRepository;
import com.orbit.auth.application.port.out.AppleTokenApiException;
import com.orbit.auth.application.port.out.KakaoUnlinkException;
import com.orbit.auth.application.port.out.ProviderUnlinkRepository;
import com.orbit.auth.application.port.out.RevokeAppleTokenPort;
import com.orbit.auth.application.port.out.UnlinkKakaoAccountPort;
import com.orbit.auth.domain.AccountId;
import com.orbit.auth.domain.OAuthProvider;
import com.orbit.auth.domain.ProviderUnlink;

import lombok.extern.slf4j.Slf4j;

/**
 * 연결 해제 대기 하나를 처리하는 단계. 커밋 직후 시도와 주기 재시도가 함께 쓴다. 제공자가 끊었거나 거절하면 대기를 지우고, 일시 실패면 다음 재시도를 잡으며, 요청 7일
 * 뒤를 넘기면 포기하고 대기와 남은 Apple 토큰을 지운다.
 *
 * <p>외부 HTTP를 기다리므로 트랜잭션을 두지 않고 각 저장은 저장소 단위로 확정한다. 같은 대기를 여러 곳에서 동시에 처리해도 제공자 호출(이미 끊긴 카카오 사용자·철회된
 * Apple 토큰)과 저장(없는 대기의 변경·삭제)이 멱등이라 결과가 같다. 로그에는 계정 식별자와 제공자만 남긴다.
 */
@Slf4j
final class ProviderUnlinker {

    private enum Outcome {
        DONE,
        RETRY
    }

    private final ProviderUnlinkRepository providerUnlinks;
    private final UnlinkKakaoAccountPort kakao;
    private final AppleRefreshTokenRepository appleTokens;
    private final RevokeAppleTokenPort appleRevoke;
    private final Clock clock;

    ProviderUnlinker(
            ProviderUnlinkRepository providerUnlinks,
            UnlinkKakaoAccountPort kakao,
            AppleRefreshTokenRepository appleTokens,
            RevokeAppleTokenPort appleRevoke,
            Clock clock) {
        this.providerUnlinks = providerUnlinks;
        this.kakao = kakao;
        this.appleTokens = appleTokens;
        this.appleRevoke = appleRevoke;
        this.clock = clock;
    }

    /** 대기 하나를 처리한다. 예상하지 못한 오류는 기록만 하고 대기를 그대로 두어 다음 주기에 다시 시도하게 한다. */
    void process(ProviderUnlink unlink) {
        try {
            Outcome outcome =
                    switch (unlink.provider()) {
                        case KAKAO -> unlinkKakao(unlink);
                        case APPLE -> revokeApple(unlink.accountId());
                    };
            if (outcome == Outcome.DONE) {
                providerUnlinks.delete(unlink.accountId(), unlink.provider());
                return;
            }
            unlink.afterFailure(clock.instant()).ifPresentOrElse(providerUnlinks::update, () -> giveUp(unlink));
        } catch (RuntimeException exception) {
            log.error(
                    "Provider unlink failed unexpectedly: accountId={}, provider={}, error={}",
                    unlink.accountId().value(),
                    unlink.provider(),
                    exception.getClass().getSimpleName());
        }
    }

    private Outcome unlinkKakao(ProviderUnlink unlink) {
        try {
            kakao.unlink(unlink.subject());
            return Outcome.DONE;
        } catch (KakaoUnlinkException exception) {
            if (exception.failure() == KakaoUnlinkException.Failure.REJECTED) {
                log.info(
                        "Kakao rejected unlink; not retrying: accountId={}",
                        unlink.accountId().value());
                return Outcome.DONE;
            }
            return Outcome.RETRY;
        }
    }

    /** 보관한 refresh token을 클라이언트마다 철회한다. Apple이 거절한 토큰은 이미 무효이므로 철회한 것과 같이 지운다. */
    private Outcome revokeApple(AccountId accountId) {
        Outcome outcome = Outcome.DONE;
        for (AppleRefreshToken token : appleTokens.listByAccount(accountId)) {
            try {
                appleRevoke.revoke(token);
                appleTokens.delete(accountId, token.clientId());
            } catch (AppleTokenApiException exception) {
                if (exception.failure() == AppleTokenApiException.Failure.REJECTED) {
                    appleTokens.delete(accountId, token.clientId());
                } else {
                    outcome = Outcome.RETRY;
                }
            }
        }
        return outcome;
    }

    private void giveUp(ProviderUnlink unlink) {
        log.warn(
                "Provider unlink abandoned after retry window: accountId={}, provider={}, failedAttempts={}",
                unlink.accountId().value(),
                unlink.provider(),
                unlink.failedAttempts() + 1);
        if (unlink.provider() == OAuthProvider.APPLE) {
            appleTokens.deleteByAccount(unlink.accountId());
        }
        providerUnlinks.delete(unlink.accountId(), unlink.provider());
    }
}
