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

import com.orbit.auth.application.port.in.command.dto.AppleWebLoginStartInfo;
import com.orbit.auth.application.port.in.command.dto.StartAppleWebLoginCommand;
import com.orbit.auth.application.port.out.AppleWebAuthorizationPort;
import com.orbit.auth.application.port.out.AppleWebLoginState;
import com.orbit.auth.application.port.out.AppleWebLoginStatePort;
import com.orbit.auth.domain.HashedNonce;
import com.orbit.auth.domain.PkceChallenge;
import com.orbit.shared.error.BusinessException;
import com.orbit.shared.error.CommonErrorCode;

@ExtendWith(MockitoExtension.class)
@DisplayName("Apple 웹 로그인 시작")
class StartAppleWebLoginServiceTest {

    private static final String RETURN_URI = "orbit://auth/apple";
    private static final String CHALLENGE = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";

    @Mock
    private AppleWebLoginStatePort states;

    @Mock
    private AppleWebAuthorizationPort authorization;

    @Test
    @DisplayName("state와 브라우저 연결 해시를 키로 해시 nonce·복귀 주소·PKCE challenge를 10분 보관하고 Apple 인가 주소와 연결 값을 돌려준다")
    void storesStateAndReturnsAuthorizationUri() {
        when(authorization.authorizationUri(anyString(), any()))
                .thenAnswer(
                        call -> URI.create("https://appleid.apple.com/auth/authorize?state=" + call.getArgument(0)));
        StartAppleWebLoginService service = new StartAppleWebLoginService(states, authorization);

        AppleWebLoginStartInfo started = service.start(new StartAppleWebLoginCommand(RETURN_URI, CHALLENGE));

        ArgumentCaptor<String> state = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> bindingHash = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<AppleWebLoginState> saved = ArgumentCaptor.forClass(AppleWebLoginState.class);
        verify(states)
                .save(state.capture(), bindingHash.capture(), saved.capture(), eq(StartAppleWebLoginService.STATE_TTL));
        ArgumentCaptor<HashedNonce> nonce = ArgumentCaptor.forClass(HashedNonce.class);
        verify(authorization).authorizationUri(eq(state.getValue()), nonce.capture());
        assertThat(state.getValue()).matches("[A-Za-z0-9_-]{43}");
        assertThat(saved.getValue().nonce()).isEqualTo(nonce.getValue());
        assertThat(saved.getValue().returnUri()).isEqualTo(RETURN_URI);
        assertThat(saved.getValue().codeChallenge()).isEqualTo(new PkceChallenge(CHALLENGE));
        assertThat(bindingHash.getValue())
                .isEqualTo(HashedNonce.fromRaw(started.browserBinding()).value())
                .isNotEqualTo(started.browserBinding());
        assertThat(started.authorizationUri()).hasParameter("state", state.getValue());
        assertThat(started.bindingLifetime()).isEqualTo(StartAppleWebLoginService.STATE_TTL);
    }

    @Test
    @DisplayName("시작할 때마다 다른 state와 연결 값을 만든다")
    void createsFreshValuesEachTime() {
        when(authorization.authorizationUri(anyString(), any())).thenReturn(URI.create("https://appleid.apple.com"));
        StartAppleWebLoginService service = new StartAppleWebLoginService(states, authorization);

        AppleWebLoginStartInfo first = service.start(new StartAppleWebLoginCommand(RETURN_URI, CHALLENGE));
        AppleWebLoginStartInfo second = service.start(new StartAppleWebLoginCommand(RETURN_URI, CHALLENGE));

        assertThat(first.browserBinding()).isNotEqualTo(second.browserBinding());
    }

    @Test
    @DisplayName("PKCE S256 challenge가 아니면 COMMON-400이고 아무것도 보관하지 않는다")
    void rejectsInvalidCodeChallenge() {
        StartAppleWebLoginService service = new StartAppleWebLoginService(states, authorization);

        assertThatThrownBy(() -> service.start(new StartAppleWebLoginCommand(RETURN_URI, "plain")))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(CommonErrorCode.BAD_REQUEST));
        verify(states, never()).save(anyString(), anyString(), any(), any());
    }
}
