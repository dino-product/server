package com.orbit.auth.application.port.out;

import java.time.Duration;
import java.util.Optional;

import com.orbit.auth.domain.OAuthProvider;

/**
 * 웹 로그인 state를 제공자별로 나눠 시작 브라우저의 연결 값 해시와 함께 만료까지 보관하고 콜백에서 한 번만 소비한다. 둘이 모두 맞아야 찾을 수 있으므로, 연결 값이
 * 없는 위조 콜백은 정상 로그인의 state를 지우지 못한다. 한 제공자의 state는 다른 제공자의 콜백에서 소비할 수 없다.
 */
public interface WebLoginStatePort {

    void save(OAuthProvider provider, String state, String browserBindingHash, WebLoginState pending, Duration ttl);

    /** 같은 제공자에서 state와 연결 값 해시가 모두 맞는 로그인을 제거하며 돌려준다. 같은 state는 두 번 소비할 수 없다. */
    Optional<WebLoginState> consume(OAuthProvider provider, String state, String browserBindingHash);
}
