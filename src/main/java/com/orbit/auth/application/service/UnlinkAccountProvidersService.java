package com.orbit.auth.application.service;

import java.time.Clock;

import org.springframework.stereotype.Service;

import com.orbit.auth.application.port.in.command.UnlinkAccountProvidersUseCase;
import com.orbit.auth.application.port.in.command.dto.UnlinkAccountProvidersCommand;
import com.orbit.auth.application.port.out.AppleRefreshTokenRepository;
import com.orbit.auth.application.port.out.ProviderUnlinkRepository;
import com.orbit.auth.application.port.out.RevokeAppleTokenPort;
import com.orbit.auth.application.port.out.UnlinkKakaoAccountPort;
import com.orbit.auth.domain.AccountId;

/**
 * 계정 폐쇄가 커밋된 직후 그 계정의 연결 해제 대기를 한 번 처리한다(Apple 토큰 철회·카카오 연결 끊기). 아직 시도하지 않은 대기만 처리하고, 실패한 적 있는
 * 대기는 같은 계정을 다시 닫아도 재시도 시각을 기다리게 둔다. 실패하면 대기를 남겨 주기 재시도에 맡기며 폐쇄는 되돌리지 않는다. 외부 HTTP를 기다리므로 메서드
 * 트랜잭션을 두지 않는다({@link ProviderUnlinker}).
 */
@Service
public class UnlinkAccountProvidersService implements UnlinkAccountProvidersUseCase {

    private final ProviderUnlinkRepository providerUnlinks;
    private final ProviderUnlinker unlinker;

    public UnlinkAccountProvidersService(
            ProviderUnlinkRepository providerUnlinks,
            UnlinkKakaoAccountPort kakao,
            AppleRefreshTokenRepository appleTokens,
            RevokeAppleTokenPort appleRevoke,
            Clock clock) {
        this.providerUnlinks = providerUnlinks;
        this.unlinker = new ProviderUnlinker(providerUnlinks, kakao, appleTokens, appleRevoke, clock);
    }

    @Override
    public void unlink(UnlinkAccountProvidersCommand command) {
        providerUnlinks.listByAccount(new AccountId(command.accountId())).stream()
                .filter(unlink -> unlink.failedAttempts() == 0)
                .forEach(unlinker::process);
    }
}
