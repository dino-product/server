package com.orbit.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.orbit.auth.application.port.in.command.dto.StartKakaoWebLoginCommand;
import com.orbit.auth.application.port.in.command.dto.WebLoginStartInfo;
import com.orbit.auth.application.port.out.KakaoWebAuthorizationPort;
import com.orbit.auth.application.port.out.WebLoginState;
import com.orbit.auth.application.port.out.WebLoginStatePort;
import com.orbit.auth.domain.HashedNonce;
import com.orbit.auth.domain.OAuthProvider;
import com.orbit.auth.domain.PkceChallenge;
import com.orbit.shared.error.BusinessException;
import com.orbit.shared.error.CommonErrorCode;

@ExtendWith(MockitoExtension.class)
@DisplayName("카카오 웹 로그인 시작")
class StartKakaoWebLoginServiceTest {

    private static final String RETURN_URI = "https://admin.example.com/login/kakao";
    private static final String CHALLENGE = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";

    @Mock
    private WebLoginStatePort states;

    @Mock
    private KakaoWebAuthorizationPort authorization;

    @Test
    @DisplayName("카카오 키 공간에 state와 브라우저 연결 해시를 키로 nonce 해시·복귀 주소·PKCE challenge를 10분 보관하고 카카오 인가 주소를 돌려준다")
    void storesStateAndReturnsAuthorizationUri() {
        when(authorization.authorizationUri(anyString(), any()))
                .thenAnswer(call -> URI.create("https://kauth.kakao.com/oauth/authorize?state=" + call.getArgument(0)));
        StartKakaoWebLoginService service = new StartKakaoWebLoginService(states, authorization);

        WebLoginStartInfo started = service.start(new StartKakaoWebLoginCommand(RETURN_URI, CHALLENGE));

        ArgumentCaptor<String> state = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> bindingHash = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<WebLoginState> saved = ArgumentCaptor.forClass(WebLoginState.class);
        verify(states)
                .save(
                        eq(OAuthProvider.KAKAO),
                        state.capture(),
                        bindingHash.capture(),
                        saved.capture(),
                        eq(WebLoginStates.TTL));
        ArgumentCaptor<HashedNonce> nonce = ArgumentCaptor.forClass(HashedNonce.class);
        verify(authorization).authorizationUri(eq(state.getValue()), nonce.capture());
        assertThat(saved.getValue())
                .isEqualTo(new WebLoginState(nonce.getValue(), RETURN_URI, new PkceChallenge(CHALLENGE)));
        assertThat(bindingHash.getValue())
                .isEqualTo(HashedNonce.fromRaw(started.browserBinding()).value());
        assertThat(started.authorizationUri()).hasParameter("state", state.getValue());
        assertThat(started.bindingLifetime()).isEqualTo(WebLoginStates.TTL);
    }

    @Test
    @DisplayName("PKCE S256 challenge가 아니면 COMMON-400이고 아무것도 보관하지 않는다")
    void rejectsInvalidCodeChallenge() {
        StartKakaoWebLoginService service = new StartKakaoWebLoginService(states, authorization);

        assertThatThrownBy(() -> service.start(new StartKakaoWebLoginCommand(RETURN_URI, "plain")))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(CommonErrorCode.BAD_REQUEST));
        verify(states, never()).save(any(), anyString(), anyString(), any(), any());
    }
}
