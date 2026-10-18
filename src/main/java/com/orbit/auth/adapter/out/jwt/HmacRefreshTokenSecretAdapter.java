package com.orbit.auth.adapter.out.jwt;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import com.orbit.auth.application.port.out.RefreshTokenSecretPort;
import com.orbit.auth.domain.RefreshSessionId;

/**
 * refresh token 비밀값을 만들고 다음 비밀값을 HMAC-SHA256으로 파생한다. 파생 키는 Access JWT 서명 비밀값에서 용도 문자열로 따로 뽑아 서명 키와 섞이지
 * 않게 하고, 새 환경 변수를 두지 않는다. JWT 비밀값을 바꾸면 그때 30초 재전송 유예 안에 있던 재요청만 다음 비밀값이 달라져 거절된다.
 */
@Component
class HmacRefreshTokenSecretAdapter implements RefreshTokenSecretPort {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String KEY_PURPOSE = "orbit-auth-refresh-token-rotation-v1";
    private static final int SECRET_BYTES = 32;

    private final SecretKeySpec derivationKey;
    private final Duration idleLifetime;
    private final SecureRandom random = new SecureRandom();

    HmacRefreshTokenSecretAdapter(JwtProperties jwtProperties, RefreshTokenProperties refreshTokenProperties) {
        byte[] jwtSecret = jwtProperties.secret().getBytes(StandardCharsets.UTF_8);
        this.derivationKey = new SecretKeySpec(
                hmac(new SecretKeySpec(jwtSecret, HMAC_ALGORITHM), KEY_PURPOSE.getBytes(StandardCharsets.UTF_8)),
                HMAC_ALGORITHM);
        this.idleLifetime = refreshTokenProperties.idleLifetime();
    }

    @Override
    public String newSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        random.nextBytes(bytes);
        return encode(bytes);
    }

    @Override
    public String next(RefreshSessionId sessionId, String secret) {
        return encode(hmac(derivationKey, (sessionId.value() + "." + secret).getBytes(StandardCharsets.UTF_8)));
    }

    @Override
    public Duration idleLifetime() {
        return idleLifetime;
    }

    private static byte[] hmac(SecretKeySpec key, byte[] input) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(key);
            return mac.doFinal(input);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Cannot derive refresh token secret");
        }
    }

    private static String encode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
