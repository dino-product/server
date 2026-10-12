package com.orbit.auth.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.orbit.auth.application.port.in.command.dto.UnlinkAccountProvidersCommand;
import com.orbit.auth.application.port.out.AppleRefreshToken;
import com.orbit.auth.application.port.out.AppleRefreshTokenRepository;
import com.orbit.auth.application.port.out.AppleTokenApiException;
import com.orbit.auth.application.port.out.KakaoUnlinkException;
import com.orbit.auth.application.port.out.ProviderUnlinkRepository;
import com.orbit.auth.application.port.out.RevokeAppleTokenPort;
import com.orbit.auth.application.port.out.UnlinkKakaoAccountPort;
import com.orbit.auth.domain.AccountId;
import com.orbit.auth.domain.ExternalIdentity;
import com.orbit.auth.domain.OAuthProvider;
import com.orbit.auth.domain.ProviderUnlink;

@ExtendWith(MockitoExtension.class)
@DisplayName("닫힌 계정의 외부 연결 해제")
class UnlinkAccountProvidersServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-11T03:00:00Z");
    private static final AccountId ACCOUNT_ID = new AccountId(7L);
    private static final String KAKAO_USER_ID = "4012345678";
    private static final ProviderUnlink KAKAO = ProviderUnlink.request(
            ACCOUNT_ID, new ExternalIdentity(OAuthProvider.KAKAO, KAKAO_USER_ID), NOW.minusSeconds(1));
    private static final ProviderUnlink APPLE = ProviderUnlink.request(
            ACCOUNT_ID, new ExternalIdentity(OAuthProvider.APPLE, "001234.abcdef.0123"), NOW.minusSeconds(1));
    private static final AppleRefreshToken IOS_TOKEN = new AppleRefreshToken("bundle", "ios-refresh");
    private static final AppleRefreshToken WEB_TOKEN = new AppleRefreshToken("services", "web-refresh");

    @Mock
    private ProviderUnlinkRepository providerUnlinks;

    @Mock
    private UnlinkKakaoAccountPort kakao;

    @Mock
    private AppleRefreshTokenRepository appleTokens;

    @Mock
    private RevokeAppleTokenPort appleRevoke;

    private UnlinkAccountProvidersService service;

    @BeforeEach
    void setUp() {
        service = new UnlinkAccountProvidersService(
                providerUnlinks, kakao, appleTokens, appleRevoke, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("카카오 연결을 끊으면 대기를 지운다")
    void deletesKakaoUnlinkAfterSuccess() {
        when(providerUnlinks.listByAccount(ACCOUNT_ID)).thenReturn(List.of(KAKAO));

        service.unlink(new UnlinkAccountProvidersCommand(7L));

        verify(kakao).unlink(KAKAO_USER_ID);
        verify(providerUnlinks).delete(ACCOUNT_ID, OAuthProvider.KAKAO);
        verify(providerUnlinks, never()).update(any());
    }

    @Test
    @DisplayName("카카오가 거절하면 다시 시도하지 않고 대기를 지운다")
    void deletesKakaoUnlinkWhenRejected() {
        when(providerUnlinks.listByAccount(ACCOUNT_ID)).thenReturn(List.of(KAKAO));
        doThrow(new KakaoUnlinkException(KakaoUnlinkException.Failure.REJECTED, "rejected"))
                .when(kakao)
                .unlink(KAKAO_USER_ID);

        service.unlink(new UnlinkAccountProvidersCommand(7L));

        verify(providerUnlinks).delete(ACCOUNT_ID, OAuthProvider.KAKAO);
    }

    @Test
    @DisplayName("카카오 호출이 일시적으로 실패하면 대기를 남기고 1분 뒤 재시도를 잡는다")
    void schedulesRetryWhenKakaoUnavailable() {
        when(providerUnlinks.listByAccount(ACCOUNT_ID)).thenReturn(List.of(KAKAO));
        doThrow(new KakaoUnlinkException(KakaoUnlinkException.Failure.UNAVAILABLE, "unavailable"))
                .when(kakao)
                .unlink(KAKAO_USER_ID);

        service.unlink(new UnlinkAccountProvidersCommand(7L));

        verify(providerUnlinks).update(KAKAO.afterFailure(NOW).orElseThrow());
        verify(providerUnlinks, never()).delete(any(), any());
    }

    @Test
    @DisplayName("Apple은 보관한 refresh token을 클라이언트마다 철회해 지우고 모두 끝나면 대기를 지운다")
    void revokesEveryAppleRefreshToken() {
        when(providerUnlinks.listByAccount(ACCOUNT_ID)).thenReturn(List.of(APPLE));
        when(appleTokens.listByAccount(ACCOUNT_ID)).thenReturn(List.of(IOS_TOKEN, WEB_TOKEN));

        service.unlink(new UnlinkAccountProvidersCommand(7L));

        verify(appleRevoke).revoke(IOS_TOKEN);
        verify(appleRevoke).revoke(WEB_TOKEN);
        verify(appleTokens).delete(ACCOUNT_ID, "bundle");
        verify(appleTokens).delete(ACCOUNT_ID, "services");
        verify(providerUnlinks).delete(ACCOUNT_ID, OAuthProvider.APPLE);
    }

    @Test
    @DisplayName("Apple이 거절한 토큰은 이미 무효이므로 지우고, 일시 실패한 토큰은 남겨 대기와 함께 다시 시도한다")
    void keepsOnlyUnavailableAppleTokens() {
        when(providerUnlinks.listByAccount(ACCOUNT_ID)).thenReturn(List.of(APPLE));
        when(appleTokens.listByAccount(ACCOUNT_ID)).thenReturn(List.of(IOS_TOKEN, WEB_TOKEN));
        doThrow(new AppleTokenApiException(AppleTokenApiException.Failure.REJECTED, "rejected"))
                .when(appleRevoke)
                .revoke(IOS_TOKEN);
        doThrow(new AppleTokenApiException(AppleTokenApiException.Failure.UNAVAILABLE, "unavailable"))
                .when(appleRevoke)
                .revoke(WEB_TOKEN);

        service.unlink(new UnlinkAccountProvidersCommand(7L));

        verify(appleTokens).delete(ACCOUNT_ID, "bundle");
        verify(appleTokens, never()).delete(ACCOUNT_ID, "services");
        verify(providerUnlinks).update(APPLE.afterFailure(NOW).orElseThrow());
        verify(providerUnlinks, never()).delete(any(), any());
    }

    @Test
    @DisplayName("보관한 Apple 토큰이 없으면 철회할 것이 없으므로 대기를 지운다")
    void deletesAppleUnlinkWithoutTokens() {
        when(providerUnlinks.listByAccount(ACCOUNT_ID)).thenReturn(List.of(APPLE));
        when(appleTokens.listByAccount(ACCOUNT_ID)).thenReturn(List.of());

        service.unlink(new UnlinkAccountProvidersCommand(7L));

        verify(appleRevoke, never()).revoke(any());
        verify(providerUnlinks).delete(ACCOUNT_ID, OAuthProvider.APPLE);
    }

    @Test
    @DisplayName("요청 7일 뒤를 넘겨 포기하면 대기와 남은 Apple 토큰을 지운다")
    void givesUpAfterRetryWindow() {
        ProviderUnlink lastChance =
                new ProviderUnlink(ACCOUNT_ID, OAuthProvider.APPLE, null, NOW.minus(Duration.ofDays(7)), 10, NOW);
        when(providerUnlinks.listByAccount(ACCOUNT_ID)).thenReturn(List.of(lastChance));
        when(appleTokens.listByAccount(ACCOUNT_ID)).thenReturn(List.of(WEB_TOKEN));
        doThrow(new AppleTokenApiException(AppleTokenApiException.Failure.UNAVAILABLE, "unavailable"))
                .when(appleRevoke)
                .revoke(WEB_TOKEN);

        service.unlink(new UnlinkAccountProvidersCommand(7L));

        verify(appleTokens).deleteByAccount(ACCOUNT_ID);
        verify(providerUnlinks).delete(ACCOUNT_ID, OAuthProvider.APPLE);
        verify(providerUnlinks, never()).update(any());
    }

    @Test
    @DisplayName("한 제공자 처리에서 예상하지 못한 오류가 나도 다른 제공자는 처리한다")
    void continuesAfterUnexpectedError() {
        when(providerUnlinks.listByAccount(ACCOUNT_ID)).thenReturn(List.of(APPLE, KAKAO));
        when(appleTokens.listByAccount(ACCOUNT_ID)).thenThrow(new IllegalStateException("db down"));

        service.unlink(new UnlinkAccountProvidersCommand(7L));

        verify(kakao).unlink(KAKAO_USER_ID);
        verify(providerUnlinks).delete(ACCOUNT_ID, OAuthProvider.KAKAO);
        verify(providerUnlinks, never()).delete(ACCOUNT_ID, OAuthProvider.APPLE);
    }
}
