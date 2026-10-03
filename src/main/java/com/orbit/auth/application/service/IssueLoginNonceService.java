package com.orbit.auth.application.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;

import org.springframework.stereotype.Service;

import com.orbit.auth.application.port.in.command.IssueLoginNonceUseCase;
import com.orbit.auth.application.port.in.command.dto.LoginNonceInfo;
import com.orbit.auth.application.port.out.LoginNoncePort;

/** 앱이 카카오 SDK 로그인 직전에 받아 가는 nonce를 발급한다. DB 트랜잭션 없이 nonce 저장소만 사용한다. */
@Service
public class IssueLoginNonceService implements IssueLoginNonceUseCase {

    static final Duration NONCE_TTL = Duration.ofMinutes(5);
    private static final int NONCE_BYTES = 32;

    private final LoginNoncePort nonces;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public IssueLoginNonceService(LoginNoncePort nonces, Clock clock) {
        this.nonces = nonces;
        this.clock = clock;
    }

    @Override
    public LoginNonceInfo issue() {
        byte[] bytes = new byte[NONCE_BYTES];
        random.nextBytes(bytes);
        String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        nonces.store(nonce, NONCE_TTL);
        return new LoginNonceInfo(nonce, clock.instant().plus(NONCE_TTL));
    }
}
