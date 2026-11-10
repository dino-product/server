package com.orbit.auth.application.port.out;

import java.time.Duration;

import com.orbit.auth.domain.RefreshSessionId;

/** refresh token 비밀값을 만들고 회전할 다음 비밀값을 파생한다. 비밀값은 로그·예외 메시지에 남기지 않는다. */
public interface RefreshTokenSecretPort {

    /** 새 세션의 추측할 수 없는 첫 비밀값을 만든다. */
    String newSecret();

    /**
     * 제출된 비밀값에서 다음 비밀값을 서버 비밀키로 결정적으로 만든다. 같은 세션·비밀값이면 언제나 같은 값이고, 비밀키 없이 제출된 비밀값만으로는 만들 수 없다.
     */
    String next(RefreshSessionId sessionId, String secret);

    /** 마지막 발급부터 이 기간 동안 쓰지 않으면 세션이 만료된다. */
    Duration idleLifetime();
}
