package com.orbit.auth.application.port.out;

import java.util.Optional;

public interface VerifyAppleIdTokenPort {

    /** 서명·발급자·대상·시간이 모두 유효하고 sub·nonce가 있으면 클레임을, 하나라도 어긋나면 빈 값을 반환한다. */
    Optional<AppleIdTokenClaims> verify(String idToken);
}
