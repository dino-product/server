package com.orbit.auth.application.port.out;

import java.time.Duration;

/** 앱이 카카오 SDK 로그인에 넘길 nonce를 만료와 함께 보관하고 id_token 검증 시 한 번만 소비한다. */
public interface LoginNoncePort {

    void store(String nonce, Duration ttl);

    /** 보관 중인 nonce를 제거하며 존재했는지 돌려준다. 같은 nonce는 두 번 소비할 수 없다. */
    boolean consume(String nonce);
}
