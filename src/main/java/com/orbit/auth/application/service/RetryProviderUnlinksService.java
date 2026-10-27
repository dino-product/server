package com.orbit.auth.application.service;

import java.time.Clock;

import org.springframework.stereotype.Service;

import com.orbit.auth.application.port.in.command.RetryProviderUnlinksUseCase;
import com.orbit.auth.application.port.out.AppleRefreshTokenRepository;
import com.orbit.auth.application.port.out.ProviderUnlinkRepository;
import com.orbit.auth.application.port.out.RevokeAppleTokenPort;
import com.orbit.auth.application.port.out.UnlinkKakaoAccountPort;

/**
 * 다음 시도 시각이 지난 연결 해제 대기를 다시 처리한다. 주기 작업이 부르며, 한 번에 정한 개수만 처리하고 남은 대기는 다음 주기에 넘긴다. 외부 HTTP를 기다리므로
 * 메서드 트랜잭션을 두지 않는다({@link ProviderUnlinker}).
 */
@Service
public class RetryProviderUnlinksService implements RetryProviderUnlinksUseCase {

    static final int BATCH_SIZE = 50;

    private final ProviderUnlinkRepository providerUnlinks;
    private final ProviderUnlinker unlinker;
    private final Clock clock;

    public RetryProviderUnlinksService(
            ProviderUnlinkRepository providerUnlinks,
            UnlinkKakaoAccountPort kakao,
            AppleRefreshTokenRepository appleTokens,
            RevokeAppleTokenPort appleRevoke,
            Clock clock) {
        this.providerUnlinks = providerUnlinks;
        this.unlinker = new ProviderUnlinker(providerUnlinks, kakao, appleTokens, appleRevoke, clock);
        this.clock = clock;
    }

    @Override
    public void retryDue() {
        providerUnlinks.listDue(clock.instant(), BATCH_SIZE).forEach(unlinker::process);
    }
}
