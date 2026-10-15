package com.orbit.auth.application.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;

import org.springframework.stereotype.Service;

import com.orbit.auth.application.port.in.command.IssueAppleLoginNonceUseCase;
import com.orbit.auth.application.port.in.command.dto.LoginNonceInfo;
import com.orbit.auth.application.port.out.AppleLoginNoncePort;
import com.orbit.auth.domain.HashedNonce;

/**
 * 앱이 Apple 로그인 직전에 받아 가는 raw nonce를 발급한다. 앱은 SHA-256 hex를 Apple 요청에 넣고 id_token에도 해시가 실리므로 저장소에는 해시만 보관한다.
 * DB 트랜잭션 없이 nonce 저장소만 사용한다.
 */
@Service
public class IssueAppleLoginNonceService implements IssueAppleLoginNonceUseCase {

    static final Duration NONCE_TTL = Duration.ofMinutes(5);
    private static final int NONCE_BYTES = 32;

    private final AppleLoginNoncePort nonces;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public IssueAppleLoginNonceService(AppleLoginNoncePort nonces, Clock clock) {
        this.nonces = nonces;
        this.clock = clock;
    }

    @Override
    public LoginNonceInfo issue() {
        byte[] bytes = new byte[NONCE_BYTES];
        random.nextBytes(bytes);
        String rawNonce = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        nonces.store(HashedNonce.fromRaw(rawNonce), NONCE_TTL);
        return new LoginNonceInfo(rawNonce, clock.instant().plus(NONCE_TTL));
    }
}
