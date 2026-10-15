package com.orbit.auth.adapter.out.apple;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

/**
 * Apple 토큰 API의 client_secret을 만든다. {@code .p8} 개인키로 ES256 서명한 JWT(kid, iss=Team ID, sub=client_id,
 * aud=https://appleid.apple.com)이며, 클라이언트별로 만들어 유효 기간의 절반이 지날 때까지 재사용한다. 개인키는 처음 쓸 때 해석하고 오류 메시지에 키 내용을
 * 남기지 않는다.
 */
@Component
class AppleClientSecretFactory {

    static final String AUDIENCE = "https://appleid.apple.com";
    private static final String PEM_HEADER = "-----BEGIN PRIVATE KEY-----";
    private static final String PEM_FOOTER = "-----END PRIVATE KEY-----";

    private final AppleClientSecretProperties properties;
    private final Clock clock;
    private final Map<String, CachedSecret> secrets = new ConcurrentHashMap<>();
    private volatile ECDSASigner signer;

    AppleClientSecretFactory(AppleClientSecretProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    String create(String clientId) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        CachedSecret cached = secrets.get(clientId);
        if (cached != null && !now.isAfter(cached.renewAfter())) {
            return cached.value();
        }
        CachedSecret created = sign(clientId, now);
        secrets.put(clientId, created);
        return created.value();
    }

    private CachedSecret sign(String clientId, Instant issuedAt) {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(properties.teamId())
                .subject(clientId)
                .audience(AUDIENCE)
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(issuedAt.plus(properties.ttl())))
                .build();
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.ES256)
                        .keyID(properties.keyId())
                        .build(),
                claims);
        try {
            jwt.sign(signer());
        } catch (JOSEException exception) {
            throw new IllegalStateException("Cannot sign Apple client_secret", exception);
        }
        return new CachedSecret(jwt.serialize(), issuedAt.plus(properties.ttl().dividedBy(2)));
    }

    private ECDSASigner signer() throws JOSEException {
        ECDSASigner current = signer;
        if (current == null) {
            current = new ECDSASigner(parsePrivateKey(properties.privateKey()));
            signer = current;
        }
        return current;
    }

    private static ECPrivateKey parsePrivateKey(String value) {
        String base64 = value.replace("\\n", "\n")
                .replace(PEM_HEADER, "")
                .replace(PEM_FOOTER, "")
                .replaceAll("\\s", "");
        try {
            byte[] der = Base64.getDecoder().decode(base64);
            return (ECPrivateKey) KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (IllegalArgumentException | GeneralSecurityException | ClassCastException exception) {
            // 키 내용이 로그·응답에 남지 않도록 원인 예외를 붙이지 않는다.
            throw new IllegalStateException("app.auth.apple.client-secret.private-key is not a PKCS#8 EC private key");
        }
    }

    private record CachedSecret(String value, Instant renewAfter) {}
}
