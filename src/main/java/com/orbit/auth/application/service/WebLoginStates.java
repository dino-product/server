package com.orbit.auth.application.service;

import java.time.Duration;

import com.orbit.auth.application.error.AuthErrorCode;
import com.orbit.auth.application.port.out.WebLoginState;
import com.orbit.auth.application.port.out.WebLoginStatePort;
import com.orbit.auth.domain.HashedNonce;
import com.orbit.auth.domain.OAuthProvider;
import com.orbit.auth.domain.PkceChallenge;
import com.orbit.shared.error.BusinessException;
import com.orbit.shared.error.CommonErrorCode;

/**
 * 서버가 제공자 인가·콜백을 맡는 웹 로그인의 state 단계. 시작할 때 일회성 state와 nonce, 시작한 브라우저에만 남길 연결 값을 만들어 state와 연결 값 해시를
 * 함께 키로 nonce 해시·복귀 주소·클라이언트의 PKCE challenge를 10분 보관하고, 콜백에서 같은 브라우저가 가져온 state만 한 번 소비한다.
 */
final class WebLoginStates {

    static final Duration TTL = Duration.ofMinutes(10);

    private final OAuthProvider provider;
    private final WebLoginStatePort states;

    WebLoginStates(OAuthProvider provider, WebLoginStatePort states) {
        this.provider = provider;
        this.states = states;
    }

    /** challenge가 S256 형식이 아니면 COMMON-400이고 아무것도 보관하지 않는다. */
    StartedWebLogin start(String returnUri, String codeChallenge) {
        PkceChallenge challenge = PkceChallenge.parse(codeChallenge)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.BAD_REQUEST));
        String state = LoginSecrets.random();
        HashedNonce nonce = HashedNonce.fromRaw(LoginSecrets.random());
        String browserBinding = LoginSecrets.random();
        states.save(
                provider,
                state,
                HashedNonce.fromRaw(browserBinding).value(),
                new WebLoginState(nonce, returnUri, challenge),
                TTL);
        return new StartedWebLogin(state, nonce, browserBinding);
    }

    /** state나 연결 값이 없거나, 재사용·만료됐거나 다른 브라우저의 연결 값이면 복귀 주소를 믿을 수 없으므로 AUTH-003이다. */
    WebLoginState consume(String state, String browserBinding) {
        if (isBlank(state) || isBlank(browserBinding)) {
            throw new BusinessException(AuthErrorCode.INVALID_NONCE);
        }
        return states.consume(
                        provider, state, HashedNonce.fromRaw(browserBinding).value())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_NONCE));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** 인가 요청에 실을 state·nonce 해시와 시작 브라우저에 남길 연결 값. */
    record StartedWebLogin(String state, HashedNonce nonce, String browserBinding) {

        @Override
        public String toString() {
            return "StartedWebLogin[state=***, browserBinding=***]";
        }
    }
}
