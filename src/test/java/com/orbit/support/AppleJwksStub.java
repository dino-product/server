package com.orbit.support;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;

/**
 * Apple JWKS 엔드포인트를 로컬 HTTP 서버로 대체한다. 서명키만 바꿔 끼우고 검증기·JWKS 조회 경로는 실제 구현을 그대로 거친다. 실제 Apple과의 E2E는 아니다.
 * 사용한 테스트 클래스가 끝나면 {@link #close()}로 서버를 정리한다.
 */
public final class AppleJwksStub implements AutoCloseable {

    public static final String ISSUER = "https://appleid.apple.com";
    public static final String BUNDLE_ID = "test-apple-bundle-id";
    public static final String SERVICES_ID = "test-apple-services-id";

    private final RSAKey signingKey;
    private final RSAKey foreignKey;
    private final HttpServer server;

    private AppleJwksStub(RSAKey signingKey, RSAKey foreignKey, HttpServer server) {
        this.signingKey = signingKey;
        this.foreignKey = foreignKey;
        this.server = server;
    }

    public static AppleJwksStub start() {
        try {
            RSAKey signingKey =
                    new RSAKeyGenerator(2048).keyID("apple-test-key").generate();
            RSAKey foreignKey =
                    new RSAKeyGenerator(2048).keyID("apple-test-key").generate();
            byte[] jwks = new JWKSet(signingKey.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/auth/keys", exchange -> {
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, jwks.length);
                try (OutputStream body = exchange.getResponseBody()) {
                    body.write(jwks);
                }
            });
            server.start();
            return new AppleJwksStub(signingKey, foreignKey, server);
        } catch (JOSEException | IOException exception) {
            throw new IllegalStateException("Cannot start Apple JWKS stub", exception);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }

    public String jwkSetUri() {
        return baseUri() + "/auth/keys";
    }

    public String baseUri() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** Apple이 iOS 앱(Bundle ID)에 발급한 것처럼 유효한 id_token을 만든다. nonce에는 앱이 넣은 값을 그대로 싣는다. */
    public String idToken(String subject, String nonce) {
        return idToken(subject, nonce, BUNDLE_ID);
    }

    /** 지정한 클라이언트를 대상으로 유효한 id_token을 만든다. */
    public String idToken(String subject, String nonce, String audience) {
        return sign(signingKey, claims(subject, nonce, audience).build());
    }

    /** kid는 같지만 Apple JWKS에 없는 키로 서명한 위조 id_token을 만든다. */
    public String forgedIdToken(String subject, String nonce) {
        return sign(foreignKey, claims(subject, nonce, BUNDLE_ID).build());
    }

    private static JWTClaimsSet.Builder claims(String subject, String nonce, String audience) {
        Instant now = Instant.now();
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .audience(audience)
                .subject(subject)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(600)));
        return nonce == null ? builder : builder.claim("nonce", nonce).claim("nonce_supported", true);
    }

    private static String sign(RSAKey key, JWTClaimsSet claims) {
        try {
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256)
                            .keyID(key.getKeyID())
                            .build(),
                    claims);
            jwt.sign(new RSASSASigner(key));
            return jwt.serialize();
        } catch (JOSEException exception) {
            throw new IllegalStateException("Cannot sign test id_token", exception);
        }
    }
}
