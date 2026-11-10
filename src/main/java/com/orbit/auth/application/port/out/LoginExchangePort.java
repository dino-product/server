package com.orbit.auth.application.port.out;

import java.time.Duration;
import java.util.Optional;

import com.orbit.auth.domain.OAuthProvider;

/**
 * 웹 로그인 콜백이 클라이언트에 넘긴 일회성 교환 코드를 제공자별로 짧게 보관하고 한 번만 소비한다. Access Token 자체는 보관하지 않으며, 한 제공자의 교환 코드는
 * 다른 제공자의 교환 경로에서 쓸 수 없다.
 */
public interface LoginExchangePort {

    void save(OAuthProvider provider, String code, PendingWebLogin login, Duration ttl);

    Optional<PendingWebLogin> consume(OAuthProvider provider, String code);
}
