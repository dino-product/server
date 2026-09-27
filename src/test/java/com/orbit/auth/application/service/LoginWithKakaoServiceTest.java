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
import com.orbit.auth.application.port.in.command.dto.LoginWithKakaoCommand;
import com.orbit.auth.application.port.out.AccessTokenPort;
import com.orbit.auth.application.port.out.AccountRepository;
import com.orbit.auth.application.port.out.IssuedAccessToken;
import com.orbit.auth.application.port.out.KakaoIdTokenClaims;
import com.orbit.auth.application.port.out.LoginNoncePort;
import com.orbit.auth.application.port.out.VerifyKakaoIdTokenPort;
import com.orbit.auth.domain.AccessToken;
import com.orbit.auth.domain.Account;
import com.orbit.auth.domain.AccountId;
import com.orbit.auth.domain.ExternalIdentity;
import com.orbit.auth.domain.OAuthProvider;
import com.orbit.shared.error.BusinessException;

@ExtendWith(MockitoExtension.class)
@DisplayName("카카오 로그인")
class LoginWithKakaoServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-27T01:00:00Z");
    private static final ExternalIdentity IDENTITY = new ExternalIdentity(OAuthProvider.KAKAO, "1234567890");
    private static final AccountId ACCOUNT_ID = new AccountId(7L);
    private static final LoginWithKakaoCommand COMMAND = new LoginWithKakaoCommand("id-token");
    private static final KakaoIdTokenClaims CLAIMS = new KakaoIdTokenClaims("1234567890", "nonce");
    private static final IssuedAccessToken ISSUED = new IssuedAccessToken(
            "access-token", new AccessToken("jti", ACCOUNT_ID, NOW, NOW.plus(Duration.ofHours(1))));

    @Mock
    private VerifyKakaoIdTokenPort idTokens;

    @Mock
    private LoginNoncePort nonces;

    @Mock
    private AccountRepository accounts;

    @Mock
    private AccessTokenPort accessTokens;

    private LoginWithKakaoService service;

    @BeforeEach
    void setUp() {
        service = new LoginWithKakaoService(idTokens, nonces, accounts, accessTokens, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("처음 보는 외부 식별이면 계정을 등록하고 Access Token을 발급한다")
    void registersAccountOnFirstLogin() {
        when(idTokens.verify("id-token")).thenReturn(Optional.of(CLAIMS));
        when(nonces.consume("nonce")).thenReturn(true);
        when(accounts.findByIdentity(IDENTITY)).thenReturn(Optional.empty());
        when(accounts.save(any())).thenReturn(Account.reconstitute(ACCOUNT_ID, List.of(IDENTITY), NOW));
        when(accessTokens.issue(ACCOUNT_ID)).thenReturn(ISSUED);

        LoginInfo info = service.login(COMMAND);

        ArgumentCaptor<Account> saved = ArgumentCaptor.forClass(Account.class);
        verify(accounts).save(saved.capture());
        assertThat(saved.getValue().identities()).containsExactly(IDENTITY);
        assertThat(saved.getValue().registeredAt()).isEqualTo(NOW);
        assertThat(info).isEqualTo(new LoginInfo(7L, true, "access-token", NOW.plus(Duration.ofHours(1))));
    }

    @Test
    @DisplayName("이미 연결된 외부 식별이면 같은 계정으로 Access Token을 발급한다")
    void reusesExistingAccount() {
        when(idTokens.verify("id-token")).thenReturn(Optional.of(CLAIMS));
        when(nonces.consume("nonce")).thenReturn(true);
        when(accounts.findByIdentity(IDENTITY))
                .thenReturn(Optional.of(Account.reconstitute(ACCOUNT_ID, List.of(IDENTITY), NOW.minusSeconds(1))));
        when(accessTokens.issue(ACCOUNT_ID)).thenReturn(ISSUED);

        LoginInfo info = service.login(COMMAND);

        verify(accounts, never()).save(any());
        assertThat(info.accountId()).isEqualTo(7L);
        assertThat(info.registered()).isFalse();
    }

    @Test
    @DisplayName("id_token이 유효하지 않으면 nonce를 소비하지 않고 거부한다")
    void rejectsInvalidIdToken() {
        when(idTokens.verify("id-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(COMMAND))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(AuthErrorCode.INVALID_ID_TOKEN));
        verify(nonces, never()).consume(any());
        verify(accounts, never()).save(any());
    }

    @Test
    @DisplayName("서버가 발급하지 않았거나 이미 쓴 nonce면 계정을 만들지 않고 거부한다")
    void rejectsUnknownNonce() {
        when(idTokens.verify("id-token")).thenReturn(Optional.of(CLAIMS));
        when(nonces.consume("nonce")).thenReturn(false);

        assertThatThrownBy(() -> service.login(COMMAND))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(AuthErrorCode.INVALID_NONCE));
        verify(accounts, never()).findByIdentity(any());
        verify(accounts, never()).save(any());
        verify(accessTokens, never()).issue(any());
    }
}
