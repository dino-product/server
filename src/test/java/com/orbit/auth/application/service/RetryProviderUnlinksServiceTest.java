package com.orbit.auth.application.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.orbit.auth.application.port.out.AppleRefreshTokenRepository;
import com.orbit.auth.application.port.out.ProviderUnlinkRepository;
import com.orbit.auth.application.port.out.RevokeAppleTokenPort;
import com.orbit.auth.application.port.out.UnlinkKakaoAccountPort;
import com.orbit.auth.domain.AccountId;
import com.orbit.auth.domain.ExternalIdentity;
import com.orbit.auth.domain.OAuthProvider;
import com.orbit.auth.domain.ProviderUnlink;

@ExtendWith(MockitoExtension.class)
@DisplayName("외부 연결 해제 재시도")
class RetryProviderUnlinksServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-11T03:00:00Z");

    @Mock
    private ProviderUnlinkRepository providerUnlinks;

    @Mock
    private UnlinkKakaoAccountPort kakao;

    @Mock
    private AppleRefreshTokenRepository appleTokens;

    @Mock
    private RevokeAppleTokenPort appleRevoke;

    private RetryProviderUnlinksService service;

    @BeforeEach
    void setUp() {
        service = new RetryProviderUnlinksService(
                providerUnlinks, kakao, appleTokens, appleRevoke, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("다음 시도 시각이 지난 대기를 한 번에 정한 개수만큼 읽어 처리한다")
    void retriesDueUnlinksInBatch() {
        ProviderUnlink first = kakaoUnlink(1L, "1001");
        ProviderUnlink second = kakaoUnlink(2L, "1002");
        when(providerUnlinks.listDue(NOW, RetryProviderUnlinksService.BATCH_SIZE))
                .thenReturn(List.of(first, second));

        service.retryDue();

        verify(kakao).unlink("1001");
        verify(kakao).unlink("1002");
        verify(providerUnlinks).delete(new AccountId(1L), OAuthProvider.KAKAO);
        verify(providerUnlinks).delete(new AccountId(2L), OAuthProvider.KAKAO);
    }

    private static ProviderUnlink kakaoUnlink(long accountId, String userId) {
        return ProviderUnlink.request(
                new AccountId(accountId), new ExternalIdentity(OAuthProvider.KAKAO, userId), NOW.minusSeconds(120));
    }
}
