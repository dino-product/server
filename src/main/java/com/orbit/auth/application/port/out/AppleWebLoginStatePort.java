package com.orbit.auth.application.port.out;

import java.time.Duration;
import java.util.Optional;

/** Apple 웹 로그인 state를 만료와 함께 보관하고 콜백에서 한 번만 소비한다. */
public interface AppleWebLoginStatePort {

    void save(String state, AppleWebLoginState pending, Duration ttl);

    /** 보관 중인 state를 제거하며 내용을 돌려준다. 같은 state는 두 번 소비할 수 없다. */
    Optional<AppleWebLoginState> consume(String state);
}
