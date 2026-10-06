package com.orbit.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.orbit.auth.application.error.AuthErrorCode;
import com.orbit.auth.application.port.in.command.dto.AppleWebLoginCompletion;
import com.orbit.auth.application.port.in.command.dto.CompleteAppleWebLoginCommand;
import com.orbit.auth.application.port.out.AccessTokenPort;
import com.orbit.auth.application.port.out.AccountRepository;
import com.orbit.auth.application.port.out.AppleCodeExchange;
import com.orbit.auth.application.port.out.AppleIdTokenClaims;
import com.orbit.auth.application.port.out.AppleLoginExchangePort;
import com.orbit.auth.application.port.out.AppleRefreshTokenRepository;
import com.orbit.auth.application.port.out.AppleTokenApiException;
import com.orbit.auth.application.port.out.AppleTokenApiException.Failure;
import com.orbit.auth.application.port.out.AppleWebAuthorizationPort;
import com.orbit.auth.application.port.out.AppleWebLoginState;
import com.orbit.auth.application.port.out.AppleWebLoginStatePort;
import com.orbit.auth.application.port.out.ExchangeAppleAuthorizationCodePort;
import com.orbit.auth.application.port.out.PendingAppleLogin;
import com.orbit.auth.application.port.out.VerifyAppleIdTokenPort;
import com.orbit.auth.domain.Account;
import com.orbit.auth.domain.AccountId;
import com.orbit.auth.domain.ExternalIdentity;
import com.orbit.auth.domain.HashedNonce;
import com.orbit.auth.domain.OAuthProvider;
import com.orbit.auth.domain.PkceChallenge;
import com.orbit.shared.error.BusinessException;

@ExtendWith(MockitoExtension.class)
@DisplayName("Apple 웹 로그인 콜백")
class CompleteAppleWebLoginServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-04T01:00:00Z");
    private static final String SUBJECT = "001234.0123456789abcdef0123456789abcdef.0123";
    private static final String SERVICES_ID = "com.orbit.web";
    private static final String REDIRECT_URI = "https://api.example.com/api/v1/auth/apple/callback";
    private static final String RETURN_URI = "https://admin.example.com/login/apple";
    private static final String BINDING = "browser-binding";
    private static final HashedNonce NONCE = HashedNonce.fromRaw("raw-nonce");
    private static final String BINDING_HASH = HashedNonce.fromRaw(BINDING).value();
    private static final PkceChallenge CHALLENGE = new PkceChallenge("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM");
    private static final AppleWebLoginState STATE = new AppleWebLoginState(NONCE, RETURN_URI, CHALLENGE);
    private static final ExternalIdentity IDENTITY = new ExternalIdentity(OAuthProvider.APPLE, SUBJECT);
    private static final AccountId ACCOUNT_ID = new AccountId(7L);

    @Mock
    private AppleWebLoginStatePort states;

    @Mock
    private AppleWebAuthorizationPort authorization;

    @Mock
    private VerifyAppleIdTokenPort idTokens;

    @Mock
    private ExchangeAppleAuthorizationCodePort codeExchanges;

    @Mock
    private AppleRefreshTokenRepository refreshTokens;

    @Mock
    private AppleLoginExchangePort loginExchanges;

    @Mock
    private AccountRepository accounts;

    @Mock
    private AccessTokenPort accessTokens;

    private CompleteAppleWebLoginService service;

    @BeforeEach
    void setUp() {
        lenient().when(authorization.clientId()).thenReturn(SERVICES_ID);
        lenient().when(authorization.redirectUri()).thenReturn(REDIRECT_URI);
        service = new CompleteAppleWebLoginService(
                states,
                authorization,
                idTokens,
                codeExchanges,
                refreshTokens,
                loginExchanges,
                accounts,
                accessTokens,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("state·연결·id_token·nonce·code가 맞으면 계정을 등록하고 refresh token을 보관한 뒤 일회성 교환 코드로 복귀한다")
    void completesLoginWithOneTimeExchangeCode() {
        givenState();
        givenValidIdToken();
        when(codeExchanges.exchange(SERVICES_ID, "apple-code", REDIRECT_URI))
                .thenReturn(new AppleCodeExchange(SUBJECT, SERVICES_ID, "apple-refresh"));
        when(accounts.findByIdentity(IDENTITY)).thenReturn(Optional.empty());
        when(accounts.saveNew(any())).thenReturn(Account.reconstitute(ACCOUNT_ID, List.of(IDENTITY), NOW));

        AppleWebLoginCompletion completion = service.complete(command(BINDING, null));

        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(loginExchanges)
                .save(
                        code.capture(),
                        eq(new PendingAppleLogin(ACCOUNT_ID, true, CHALLENGE)),
                        eq(CompleteAppleWebLoginService.EXCHANGE_CODE_TTL));
        verify(refreshTokens).save(ACCOUNT_ID, SERVICES_ID, "apple-refresh", NOW);
        verify(accessTokens, never()).issue(any());
        assertThat(completion.returnUri()).isEqualTo(RETURN_URI);
        assertThat(completion.exchangeCode()).isEqualTo(code.getValue()).matches("[A-Za-z0-9_-]{43}");
        assertThat(completion.errorCode()).isNull();
    }

    @Test
    @DisplayName("보관하지 않았거나 이미 쓴 state면 복귀 주소를 모르므로 AUTH-003으로 끝낸다")
    void rejectsUnknownState() {
        when(states.consume("state", BINDING_HASH)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.complete(command(BINDING, null)))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(AuthErrorCode.INVALID_NONCE));
        verify(idTokens, never()).verify(anyString());
    }

    @Test
    @DisplayName("로그인을 시작한 브라우저의 연결 값이 없거나 다르면 복귀하지 않고 AUTH-003으로 끝낸다")
    void rejectsCallbackFromAnotherBrowser() {
        when(states.consume("state", HashedNonce.fromRaw("other-browser").value()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.complete(command("other-browser", null)))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(AuthErrorCode.INVALID_NONCE));
        assertThatThrownBy(() -> service.complete(command(null, null)))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(AuthErrorCode.INVALID_NONCE));
        verify(states, never()).consume("state", BINDING_HASH);
        verify(idTokens, never()).verify(anyString());
    }

    @Test
    @DisplayName("사용자가 Apple 로그인을 취소하면 AUTH-008로 복귀한다")
    void returnsCancellation() {
        givenState();

        AppleWebLoginCompletion completion = service.complete(command(BINDING, "user_cancelled_authorize"));

        assertThat(completion.errorCode()).isEqualTo("AUTH-008");
        assertThat(completion.exchangeCode()).isNull();
        verify(idTokens, never()).verify(anyString());
    }

    @Test
    @DisplayName("Apple이 취소가 아닌 오류(설정 오류 등)를 보내면 AUTH-006으로 복귀한다")
    void returnsAppleErrorOtherThanCancellationAsUnavailable() {
        givenState();

        assertThat(service.complete(command(BINDING, "invalid_request")).errorCode())
                .isEqualTo("AUTH-006");
        verify(idTokens, never()).verify(anyString());
    }

    @Test
    @DisplayName("authorization code가 비어 있으면 AUTH-005로 복귀한다")
    void returnsMissingAuthorizationCode() {
        givenState();
        givenValidIdToken();

        assertThat(service.complete(new CompleteAppleWebLoginCommand("state", BINDING, "id-token", " ", null))
                        .errorCode())
                .isEqualTo("AUTH-005");
        verify(codeExchanges, never()).exchange(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("예상하지 못한 내부 오류가 나도 복귀 주소로 COMMON-500을 보낸다")
    void returnsInternalErrorToClient() {
        givenState();
        givenValidIdToken();
        when(codeExchanges.exchange(SERVICES_ID, "apple-code", REDIRECT_URI))
                .thenReturn(new AppleCodeExchange(SUBJECT, SERVICES_ID, "apple-refresh"));
        when(accounts.findByIdentity(IDENTITY))
                .thenReturn(Optional.of(Account.reconstitute(ACCOUNT_ID, List.of(IDENTITY), NOW)));
        org.mockito.Mockito.doThrow(new IllegalStateException("db down"))
                .when(refreshTokens)
                .save(any(), anyString(), anyString(), any());

        AppleWebLoginCompletion completion = service.complete(command(BINDING, null));

        assertThat(completion.errorCode()).isEqualTo("COMMON-500");
        assertThat(completion.returnUri()).isEqualTo(RETURN_URI);
        verify(loginExchanges, never()).save(anyString(), any(), any(Duration.class));
    }

    @Test
    @DisplayName("id_token 검증에 실패하면 AUTH-002로 복귀한다")
    void returnsInvalidIdToken() {
        givenState();
        when(idTokens.verify("id-token")).thenReturn(Optional.empty());

        assertThat(service.complete(command(BINDING, null)).errorCode()).isEqualTo("AUTH-002");
    }

    @Test
    @DisplayName("웹 Services ID가 아닌 클라이언트(iOS)에 발급된 id_token이면 AUTH-002로 복귀한다")
    void returnsIdTokenOfAnotherClient() {
        givenState();
        when(idTokens.verify("id-token"))
                .thenReturn(Optional.of(new AppleIdTokenClaims(SUBJECT, NONCE.value(), "com.orbit.app")));

        assertThat(service.complete(command(BINDING, null)).errorCode()).isEqualTo("AUTH-002");
        verify(codeExchanges, never()).exchange(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("id_token의 nonce가 이 state의 nonce가 아니면 AUTH-003으로 복귀한다")
    void returnsNonceMismatch() {
        givenState();
        when(idTokens.verify("id-token"))
                .thenReturn(Optional.of(new AppleIdTokenClaims(
                        SUBJECT, HashedNonce.fromRaw("other").value(), SERVICES_ID)));

        assertThat(service.complete(command(BINDING, null)).errorCode()).isEqualTo("AUTH-003");
        verify(codeExchanges, never()).exchange(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Apple이 code를 거절하거나 다른 사용자의 code면 AUTH-005, Apple을 쓸 수 없으면 AUTH-006으로 복귀한다")
    void returnsCodeExchangeFailures() {
        givenState();
        givenValidIdToken();
        when(codeExchanges.exchange(SERVICES_ID, "apple-code", REDIRECT_URI))
                .thenThrow(new AppleTokenApiException(Failure.REJECTED, "invalid_grant"))
                .thenReturn(new AppleCodeExchange("other-sub", SERVICES_ID, "r"))
                .thenThrow(new AppleTokenApiException(Failure.UNAVAILABLE, "down"));

        assertThat(service.complete(command(BINDING, null)).errorCode()).isEqualTo("AUTH-005");
        givenState();
        assertThat(service.complete(command(BINDING, null)).errorCode()).isEqualTo("AUTH-005");
        givenState();
        assertThat(service.complete(command(BINDING, null)).errorCode()).isEqualTo("AUTH-006");
        verify(accounts, never()).findByIdentity(any());
        verify(loginExchanges, never()).save(anyString(), any(), any(Duration.class));
    }

    private void givenState() {
        when(states.consume("state", BINDING_HASH)).thenReturn(Optional.of(STATE));
    }

    private void givenValidIdToken() {
        when(idTokens.verify("id-token"))
                .thenReturn(Optional.of(new AppleIdTokenClaims(SUBJECT, NONCE.value(), SERVICES_ID)));
    }

    private static CompleteAppleWebLoginCommand command(String binding, String appleError) {
        return new CompleteAppleWebLoginCommand("state", binding, "id-token", "apple-code", appleError);
    }
}
