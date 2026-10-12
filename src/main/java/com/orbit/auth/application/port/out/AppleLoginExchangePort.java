package com.orbit.auth.application.port.out;

import java.time.Duration;
import java.util.Optional;

/** 웹·Android Apple 콜백이 클라이언트에 넘긴 일회성 교환 코드를 짧게 보관하고 한 번만 소비한다. Access Token 자체는 보관하지 않는다. */
public interface AppleLoginExchangePort {

    void save(String code, PendingAppleLogin login, Duration ttl);

    Optional<PendingAppleLogin> consume(String code);
}
