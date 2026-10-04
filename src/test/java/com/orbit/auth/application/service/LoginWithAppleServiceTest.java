package com.orbit.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.orbit.auth.application.error.AuthErrorCode;
import com.orbit.auth.application.port.in.command.dto.LoginInfo;
import com.orbit.auth.application.port.in.command.dto.LoginWithAppleCommand;
import com.orbit.auth.application.port.out.AccessTokenPort;
import com.orbit.auth.application.port.out.AccountRepository;
import com.orbit.auth.application.port.out.AppleIdTokenClaims;
import com.orbit.auth.application.port.out.AppleLoginNoncePort;
import com.orbit.auth.application.port.out.DuplicateIdentityException;
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
    private static final HashedNonce NONCE = HashedNonce.fromRaw("server-raw-nonce");
    private static final ExternalIdentity IDENTITY = new ExternalIdentity(OAuthProvider.APPLE, SUBJECT);
    private static final AccountId ACCOUNT_ID = new AccountId(7L);
    private static final LoginWithAppleCommand COMMAND = new LoginWithAppleCommand("id-token");
    private static final AppleIdTokenClaims CLAIMS = new AppleIdTokenClaims(SUBJECT, NONCE.value());
    private static final IssuedAccessToken ISSUED = new IssuedAccessToken(
            "access-token", new AccessToken("jti", ACCOUNT_ID, NOW, NOW.plus(Duration.ofHours(1))));

    @Mock
    private VerifyAppleIdTokenPort idTokens;

    @Mock
    private AppleLoginNoncePort nonces;

    @Mock
    private AccountRepository accounts;

    @Mock
    private AccessTokenPort accessTokens;

    private LoginWithAppleService service;

    @BeforeEach
    void setUp() {
        service = new LoginWithAppleService(idTokens, nonces, accounts, accessTokens, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("처음 보는 Apple sub면 계정을 등록하고 Access Token을 발급한다")
    void registersAccountOnFirstLogin() {
        when(idTokens.verify("id-token")).thenReturn(Optional.of(CLAIMS));
        when(nonces.consume(NONCE)).thenReturn(true);
        when(accounts.findByIdentity(IDENTITY)).thenReturn(Optional.empty());
        when(accounts.saveNew(any())).thenReturn(Account.reconstitute(ACCOUNT_ID, List.of(IDENTITY), NOW));
        when(accessTokens.issue(ACCOUNT_ID)).thenReturn(ISSUED);

        LoginInfo info = service.login(COMMAND);

        ArgumentCaptor<Account> saved = ArgumentCaptor.forClass(Account.class);
        verify(accounts).saveNew(saved.capture());
        assertThat(saved.getValue().identities()).containsExactly(IDENTITY);
        assertThat(saved.getValue().registeredAt()).isEqualTo(NOW);
        assertThat(info).isEqualTo(new LoginInfo(7L, true, "access-token", NOW.plus(Duration.ofHours(1))));
    }

    @Test
    @DisplayName("이미 연결된 Apple sub면 같은 계정으로 로그인한다")
    void reusesLinkedAccount() {
        when(idTokens.verify("id-token")).thenReturn(Optional.of(CLAIMS));
        when(nonces.consume(NONCE)).thenReturn(true);
        when(accounts.findByIdentity(IDENTITY))
                .thenReturn(Optional.of(Account.reconstitute(ACCOUNT_ID, List.of(IDENTITY), NOW)));
        when(accessTokens.issue(ACCOUNT_ID)).thenReturn(ISSUED);

        assertThat(service.login(COMMAND).registered()).isFalse();
        verify(accounts, never()).saveNew(any());
    }

    @Test
    @DisplayName("동시 첫 로그인에서 먼저 저장된 계정을 다시 조회해 쓴다")
    void reusesAccountCreatedByConcurrentFirstLogin() {
        when(idTokens.verify("id-token")).thenReturn(Optional.of(CLAIMS));
        when(nonces.consume(NONCE)).thenReturn(true);
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
    @DisplayName("id_token 검증에 실패하면 AUTH-002이고 nonce를 소비하지 않는다")
    void rejectsInvalidIdTokenWithoutConsumingNonce() {
        when(idTokens.verify("id-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(COMMAND))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(AuthErrorCode.INVALID_ID_TOKEN));
        verify(nonces, never()).consume(any());
    }

    @Test
    @DisplayName("nonce 클레임이 해시가 아니면(raw 그대로) AUTH-003이고 저장소를 조회하지 않는다")
    void rejectsUnhashedNonceClaim() {
        when(idTokens.verify("id-token")).thenReturn(Optional.of(new AppleIdTokenClaims(SUBJECT, "server-raw-nonce")));

        assertThatThrownBy(() -> service.login(COMMAND))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(AuthErrorCode.INVALID_NONCE));
        verify(nonces, never()).consume(any());
        verify(accounts, never()).findByIdentity(any());
    }

    @Test
    @DisplayName("서버가 보관하지 않은(미발급·재사용·만료) nonce면 AUTH-003이다")
    void rejectsUnknownNonce() {
        when(idTokens.verify("id-token")).thenReturn(Optional.of(CLAIMS));
        when(nonces.consume(NONCE)).thenReturn(false);

        assertThatThrownBy(() -> service.login(COMMAND))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(AuthErrorCode.INVALID_NONCE));
        verify(accounts, never()).findByIdentity(any());
    }
}
