package com.orbit.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import com.orbit.auth.application.port.in.command.dto.CompleteKakaoWebLoginCommand;
import com.orbit.auth.application.port.in.command.dto.WebLoginCompletion;
import com.orbit.auth.application.port.out.AccountRepository;
import com.orbit.auth.application.port.out.ExchangeKakaoAuthorizationCodePort;
import com.orbit.auth.application.port.out.KakaoIdTokenClaims;
import com.orbit.auth.application.port.out.KakaoTokenApiException;
import com.orbit.auth.application.port.out.KakaoTokenApiException.Failure;
import com.orbit.auth.application.port.out.LoginExchangePort;
import com.orbit.auth.application.port.out.PendingWebLogin;
import com.orbit.auth.application.port.out.VerifyKakaoIdTokenPort;
import com.orbit.auth.application.port.out.WebLoginState;
import com.orbit.auth.application.port.out.WebLoginStatePort;
import com.orbit.auth.domain.Account;
import com.orbit.auth.domain.AccountId;
import com.orbit.auth.domain.ExternalIdentity;
import com.orbit.auth.domain.HashedNonce;
import com.orbit.auth.domain.OAuthProvider;
import com.orbit.auth.domain.PkceChallenge;
import com.orbit.shared.error.BusinessException;

@ExtendWith(MockitoExtension.class)
@DisplayName("카카오 웹 로그인 콜백")
class CompleteKakaoWebLoginServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-27T01:00:00Z");
    private static final String SUBJECT = "4012345678";
    private static final ExternalIdentity IDENTITY = new ExternalIdentity(OAuthProvider.KAKAO, SUBJECT);
    private static final AccountId ACCOUNT_ID = new AccountId(11L);
    private static final String RETURN_URI = "https://admin.example.com/login/kakao";
    private static final String BINDING = "browser-binding";
    private static final String BINDING_HASH = HashedNonce.fromRaw(BINDING).value();
    private static final HashedNonce NONCE = HashedNonce.fromRaw("raw-nonce");
    private static final PkceChallenge CHALLENGE = new PkceChallenge("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM");
    private static final WebLoginState STATE = new WebLoginState(NONCE, RETURN_URI, CHALLENGE);

    @Mock
    private WebLoginStatePort states;

    @Mock
    private ExchangeKakaoAuthorizationCodePort codeExchanges;

    @Mock
    private VerifyKakaoIdTokenPort idTokens;

    @Mock
    private LoginExchangePort loginExchanges;

    @Mock
    private AccountRepository accounts;

    @Mock
    private SessionTokenIssuer tokens;

    private CompleteKakaoWebLoginService service;

    @BeforeEach
    void setUp() {
        service = new CompleteKakaoWebLoginService(
                states, codeExchanges, idTokens, loginExchanges, accounts, tokens, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("state를 소비하고 인가 코드로 받은 id_token의 nonce가 맞으면 계정을 등록하고 일회성 교환 코드로 복귀한다")
    void completesLoginWithExchangeCode() {
        givenPendingState();
        when(codeExchanges.exchange("kakao-code")).thenReturn("id-token");
        when(idTokens.verify("id-token")).thenReturn(Optional.of(new KakaoIdTokenClaims(SUBJECT, NONCE.value())));
        when(accounts.findByIdentity(IDENTITY)).thenReturn(Optional.empty());
        when(accounts.saveNew(any())).thenReturn(Account.reconstitute(ACCOUNT_ID, List.of(IDENTITY), NOW));

        WebLoginCompletion completion = service.complete(command("kakao-code", null));

        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(loginExchanges)
                .save(
                        eq(OAuthProvider.KAKAO),
                        code.capture(),
                        eq(new PendingWebLogin(ACCOUNT_ID, true, CHALLENGE)),
                        eq(WebLoginExchangeCodes.TTL));
        verify(tokens, never()).start(any());
        assertThat(completion).isEqualTo(WebLoginCompletion.success(RETURN_URI, code.getValue()));
        assertThat(code.getValue()).matches("[A-Za-z0-9_-]{43}");
    }

    @Test
    @DisplayName("보관하지 않았거나 이미 쓴 state, 다른 브라우저의 콜백은 복귀 주소를 모르므로 AUTH-003으로 끝내고 카카오를 부르지 않는다")
    void rejectsUnknownStateOrAnotherBrowser() {
        when(states.consume(OAuthProvider.KAKAO, "state", BINDING_HASH)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.complete(command("kakao-code", null)))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(AuthErrorCode.INVALID_NONCE));
        assertThatThrownBy(() -> service.complete(new CompleteKakaoWebLoginCommand("state", null, "kakao-code", null)))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(AuthErrorCode.INVALID_NONCE));
        verifyNoInteractions(codeExchanges, accounts, loginExchanges);
    }

    @Test
    @DisplayName("사용자가 동의 화면에서 취소하면(access_denied) error=AUTH-008로 복귀하고, 그 밖의 카카오 오류는 AUTH-010이다")
    void returnsCancellationAndKakaoErrors() {
        givenPendingState();
        assertThat(service.complete(command(null, "access_denied")))
                .isEqualTo(WebLoginCompletion.failure(RETURN_URI, AuthErrorCode.LOGIN_CANCELLED.getCode()));

        givenPendingState();
        assertThat(service.complete(command(null, "server_error")))
                .isEqualTo(WebLoginCompletion.failure(RETURN_URI, AuthErrorCode.KAKAO_UNAVAILABLE.getCode()));
        verifyNoInteractions(codeExchanges, accounts, loginExchanges);
    }

    @Test
    @DisplayName("카카오가 인가 코드를 거절했거나 인가 코드가 없으면 error=AUTH-002, 카카오를 쓸 수 없으면 AUTH-010으로 복귀한다")
    void mapsTokenApiFailures() {
        givenPendingState();
        when(codeExchanges.exchange("used-code")).thenThrow(new KakaoTokenApiException(Failure.REJECTED, "rejected"));
        assertThat(service.complete(command("used-code", null)).errorCode()).isEqualTo("AUTH-002");

        givenPendingState();
        when(codeExchanges.exchange("kakao-code")).thenThrow(new KakaoTokenApiException(Failure.UNAVAILABLE, "down"));
        assertThat(service.complete(command("kakao-code", null)).errorCode()).isEqualTo("AUTH-010");

        givenPendingState();
        assertThat(service.complete(command(" ", null)).errorCode()).isEqualTo("AUTH-002");
        verifyNoInteractions(accounts, loginExchanges);
    }

    @Test
    @DisplayName("서버가 받은 id_token 검증 실패는 카카오·설정 문제라 AUTH-010, nonce 불일치는 AUTH-003으로 복귀하고 계정을 만들지 않는다")
    void rejectsInvalidIdTokenOrNonce() {
        givenPendingState();
        when(codeExchanges.exchange("kakao-code")).thenReturn("id-token");
        when(idTokens.verify("id-token")).thenReturn(Optional.empty());
        assertThat(service.complete(command("kakao-code", null)).errorCode()).isEqualTo("AUTH-010");

        givenPendingState();
        when(idTokens.verify("id-token"))
                .thenReturn(Optional.of(new KakaoIdTokenClaims(
                        SUBJECT, HashedNonce.fromRaw("other").value())));
        assertThat(service.complete(command("kakao-code", null)).errorCode()).isEqualTo("AUTH-003");
        verifyNoInteractions(accounts, loginExchanges);
    }

    @Test
    @DisplayName("예상하지 못한 내부 오류도 콜백 주소에 멈추지 않고 error=COMMON-500으로 복귀한다")
    void returnsInternalErrorToClient() {
        givenPendingState();
        when(codeExchanges.exchange("kakao-code")).thenReturn("id-token");
        when(idTokens.verify("id-token")).thenReturn(Optional.of(new KakaoIdTokenClaims(SUBJECT, NONCE.value())));
        when(accounts.findByIdentity(IDENTITY)).thenThrow(new IllegalStateException("db down"));

        assertThat(service.complete(command("kakao-code", null)))
                .isEqualTo(WebLoginCompletion.failure(RETURN_URI, "COMMON-500"));
        verify(loginExchanges, never()).save(any(), anyString(), any(), any(Duration.class));
    }

    private void givenPendingState() {
        when(states.consume(OAuthProvider.KAKAO, "state", BINDING_HASH)).thenReturn(Optional.of(STATE));
    }

    private static CompleteKakaoWebLoginCommand command(String code, String error) {
        return new CompleteKakaoWebLoginCommand("state", BINDING, code, error);
    }
}
