package com.orbit.auth.application.port.out;

import java.time.Duration;

import com.orbit.auth.domain.HashedNonce;

/** Apple 로그인 nonce의 해시를 만료와 함께 보관하고 id_token 검증 시 한 번만 소비한다. 카카오 nonce와 키 공간을 공유하지 않는다. */
public interface AppleLoginNoncePort {

    void store(HashedNonce nonce, Duration ttl);

    /** 보관 중인 해시를 제거하며 존재했는지 돌려준다. 같은 nonce는 두 번 소비할 수 없다. */
    boolean consume(HashedNonce nonce);
}
