package com.orbit.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import com.orbit.auth.application.error.AuthErrorCode;
import com.orbit.auth.application.port.in.command.dto.LoginInfo;
import com.orbit.auth.application.port.in.command.dto.LoginWithAppleCommand;
import com.orbit.auth.application.port.out.AccessTokenPort;
import com.orbit.auth.application.port.out.AccountRepository;
import com.orbit.auth.application.port.out.AppleCodeExchange;
import com.orbit.auth.application.port.out.AppleIdTokenClaims;
import com.orbit.auth.application.port.out.AppleLoginNoncePort;
import com.orbit.auth.application.port.out.AppleRefreshTokenRepository;
import com.orbit.auth.application.port.out.AppleTokenApiException;
import com.orbit.auth.application.port.out.AppleTokenApiException.Failure;
import com.orbit.auth.application.port.out.AppleWebAuthorizationPort;
import com.orbit.auth.application.port.out.DuplicateIdentityException;
import com.orbit.auth.application.port.out.ExchangeAppleAuthorizationCodePort;
import com.orbit.auth.application.port.out.IssuedAccessToken;
import com.orbit.auth.application.port.out.VerifyAppleIdTokenPort;
import com.orbit.auth.domain.AccessToken;
import com.orbit.auth.domain.Account;
import com.orbit.auth.domain.AccountId;
import com.orbit.auth.domain.ExternalIdentity;
import com.orbit.auth.domain.HashedNonce;
import com.orbit.auth.domain.OAuthProvider;
import com.orbit.shared.error.BusinessException;

@ExtendWith(MockitoExtension.class)
@DisplayName("Apple 로그인")
class LoginWithAppleServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-04T01:00:00Z");
    private static final String SUBJECT = "001234.0123456789abcdef0123456789abcdef.0123";
    private static final String BUNDLE_ID = "com.orbit.app";
    private static final HashedNonce NONCE = HashedNonce.fromRaw("server-raw-nonce");
    private static final ExternalIdentity IDENTITY = new ExternalIdentity(OAuthProvider.APPLE, SUBJECT);
    private static final AccountId ACCOUNT_ID = new AccountId(7L);
    private static final LoginWithAppleCommand COMMAND = new LoginWithAppleCommand("id-token", "auth-code");
    private static final AppleIdTokenClaims CLAIMS = new AppleIdTokenClaims(SUBJECT, NONCE.value(), BUNDLE_ID);
    private static final AppleCodeExchange EXCHANGE = new AppleCodeExchange(SUBJECT, BUNDLE_ID, "apple-refresh");
    private static final IssuedAccessToken ISSUED = new IssuedAccessToken(
            "access-token", new AccessToken("jti", ACCOUNT_ID, NOW, NOW.plus(Duration.ofHours(1))));

    @Mock
    private VerifyAppleIdTokenPort idTokens;

    @Mock
    private AppleLoginNoncePort nonces;

    @Mock
    private ExchangeAppleAuthorizationCodePort codeExchanges;

    @Mock
    private AppleRefreshTokenRepository refreshTokens;

    @Mock
    private AppleWebAuthorizationPort webAuthorization;

    @Mock
    private AccountRepository accounts;

    @Mock
    private AccessTokenPort accessTokens;

    private LoginWithAppleService service;

    @BeforeEach
    void setUp() {
        lenient().when(webAuthorization.clientId()).thenReturn("com.orbit.web");
        service = new LoginWithAppleService(
                idTokens,
                nonces,
                codeExchanges,
                refreshTokens,
                webAuthorization,
                accounts,
                accessTokens,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("처음 보는 Apple sub면 code를 교환해 계정을 등록하고 refresh token을 보관한 뒤 Access Token을 발급한다")
    void registersAccountAndKeepsRefreshTokenOnFirstLogin() {
        givenValidProofs();
        when(accounts.findByIdentity(IDENTITY)).thenReturn(Optional.empty());
        when(accounts.saveNew(any())).thenReturn(Account.reconstitute(ACCOUNT_ID, List.of(IDENTITY), NOW));
        when(accessTokens.issue(ACCOUNT_ID)).thenReturn(ISSUED);

        LoginInfo info = service.login(COMMAND);

        ArgumentCaptor<Account> saved = ArgumentCaptor.forClass(Account.class);
        verify(accounts).saveNew(saved.capture());
        assertThat(saved.getValue().identities()).containsExactly(IDENTITY);
        verify(codeExchanges).exchange(BUNDLE_ID, "auth-code", null);
        verify(refreshTokens).save(ACCOUNT_ID, BUNDLE_ID, "apple-refresh", NOW);
        assertThat(info).isEqualTo(new LoginInfo(7L, true, "access-token", NOW.plus(Duration.ofHours(1))));
    }

    @Test
    @DisplayName("이미 연결된 Apple sub면 같은 계정으로 로그인하고 refresh token을 최신 값으로 바꾼다")
    void reusesLinkedAccountAndRefreshesToken() {
        givenValidProofs();
        when(accounts.findByIdentity(IDENTITY))
                .thenReturn(Optional.of(Account.reconstitute(ACCOUNT_ID, List.of(IDENTITY), NOW)));
        when(accessTokens.issue(ACCOUNT_ID)).thenReturn(ISSUED);

        assertThat(service.login(COMMAND).registered()).isFalse();
        verify(accounts, never()).saveNew(any());
        verify(refreshTokens).save(ACCOUNT_ID, BUNDLE_ID, "apple-refresh", NOW);
    }

    @Test
    @DisplayName("동시 첫 로그인에서 먼저 저장된 계정을 다시 조회해 쓴다")
    void reusesAccountCreatedByConcurrentFirstLogin() {
        givenValidProofs();
        when(accounts.findByIdentity(IDENTITY))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(Account.reconstitute(ACCOUNT_ID, List.of(IDENTITY), NOW)));
        when(accounts.saveNew(any())).thenThrow(new DuplicateIdentityException(IDENTITY, null));
        when(accessTokens.issue(ACCOUNT_ID)).thenReturn(ISSUED);

        LoginInfo info = service.login(COMMAND);

        assertThat(info.accountId()).isEqualTo(7L);
        assertThat(info.registered()).isFalse();
    }

    @Test
    @DisplayName("nonce를 소비한 뒤에 code를 교환한다")
    void consumesNonceBeforeExchangingCode() {
        givenValidProofs();
        when(accounts.findByIdentity(IDENTITY))
                .thenReturn(Optional.of(Account.reconstitute(ACCOUNT_ID, List.of(IDENTITY), NOW)));
        when(accessTokens.issue(ACCOUNT_ID)).thenReturn(ISSUED);

        service.login(COMMAND);

        InOrder order = Mockito.inOrder(nonces, codeExchanges);
        order.verify(nonces).consume(NONCE);
        order.verify(codeExchanges).exchange(BUNDLE_ID, "auth-code", null);
    }

    @Test
    @DisplayName("id_token 검증에 실패하면 AUTH-002이고 nonce를 소비하거나 code를 교환하지 않는다")
    void rejectsInvalidIdTokenWithoutConsumingNonce() {
        when(idTokens.verify("id-token")).thenReturn(Optional.empty());

        assertError(AuthErrorCode.INVALID_ID_TOKEN);
        verify(nonces, never()).consume(any());
        verify(codeExchanges, never()).exchange(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("웹 Services ID를 대상으로 발급된 id_token은 iOS 로그인에서 AUTH-002이고 nonce를 소비하지 않는다")
    void rejectsIdTokenOfWebServicesId() {
        when(idTokens.verify("id-token"))
                .thenReturn(Optional.of(new AppleIdTokenClaims(SUBJECT, NONCE.value(), "com.orbit.web")));

        assertError(AuthErrorCode.INVALID_ID_TOKEN);
        verify(nonces, never()).consume(any());
    }

    @Test
    @DisplayName("nonce 클레임이 해시가 아니면(raw 그대로) AUTH-003이고 저장소를 조회하지 않는다")
    void rejectsUnhashedNonceClaim() {
        when(idTokens.verify("id-token"))
                .thenReturn(Optional.of(new AppleIdTokenClaims(SUBJECT, "server-raw-nonce", BUNDLE_ID)));

        assertError(AuthErrorCode.INVALID_NONCE);
        verify(nonces, never()).consume(any());
        verify(codeExchanges, never()).exchange(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("서버가 보관하지 않은(미발급·재사용·만료) nonce면 AUTH-003이고 code를 교환하지 않는다")
    void rejectsUnknownNonce() {
        when(idTokens.verify("id-token")).thenReturn(Optional.of(CLAIMS));
        when(nonces.consume(NONCE)).thenReturn(false);

        assertError(AuthErrorCode.INVALID_NONCE);
        verify(codeExchanges, never()).exchange(anyString(), anyString(), any());
        verify(accounts, never()).findByIdentity(any());
    }

    @Test
    @DisplayName("Apple이 code를 거절하면 AUTH-005이고 계정을 만들지 않는다")
    void rejectsCodeRejectedByApple() {
        when(idTokens.verify("id-token")).thenReturn(Optional.of(CLAIMS));
        when(nonces.consume(NONCE)).thenReturn(true);
        when(codeExchanges.exchange(BUNDLE_ID, "auth-code", null))
                .thenThrow(new AppleTokenApiException(Failure.REJECTED, "invalid_grant"));

        assertError(AuthErrorCode.INVALID_APPLE_AUTHORIZATION_CODE);
        verify(accounts, never()).findByIdentity(any());
    }

    @Test
    @DisplayName("Apple 토큰 API를 쓸 수 없으면 AUTH-006이고 계정을 만들지 않는다")
    void reportsAppleUnavailable() {
        when(idTokens.verify("id-token")).thenReturn(Optional.of(CLAIMS));
        when(nonces.consume(NONCE)).thenReturn(true);
        when(codeExchanges.exchange(BUNDLE_ID, "auth-code", null))
                .thenThrow(new AppleTokenApiException(Failure.UNAVAILABLE, "down"));

        assertError(AuthErrorCode.APPLE_UNAVAILABLE);
        verify(accounts, never()).findByIdentity(any());
    }

    @Test
    @DisplayName("교환한 code의 sub가 id_token의 sub와 다르면 AUTH-005이고 계정을 만들지 않는다")
    void rejectsCodeOfAnotherUser() {
        when(idTokens.verify("id-token")).thenReturn(Optional.of(CLAIMS));
        when(nonces.consume(NONCE)).thenReturn(true);
        when(codeExchanges.exchange(BUNDLE_ID, "auth-code", null))
                .thenReturn(new AppleCodeExchange("other-sub", BUNDLE_ID, "apple-refresh"));

        assertError(AuthErrorCode.INVALID_APPLE_AUTHORIZATION_CODE);
        verify(accounts, never()).findByIdentity(any());
        verify(refreshTokens, never()).save(any(), anyString(), anyString(), any());
    }

    private void givenValidProofs() {
        when(idTokens.verify("id-token")).thenReturn(Optional.of(CLAIMS));
        when(nonces.consume(NONCE)).thenReturn(true);
        when(codeExchanges.exchange(BUNDLE_ID, "auth-code", null)).thenReturn(EXCHANGE);
    }

    private void assertError(AuthErrorCode expected) {
        assertThatThrownBy(() -> service.login(COMMAND))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(expected));
    }
}
