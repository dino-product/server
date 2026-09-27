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
 * 카카오 JWKS 엔드포인트를 로컬 HTTP 서버로 대체한다. 서명키만 바꿔 끼우고 검증기·JWKS 조회 경로는 실제 구현을 그대로 거친다. 실제 카카오와의 E2E는 아니다.
 */
public final class KakaoJwksStub {

    public static final String ISSUER = "https://kauth.kakao.com";
    public static final String AUDIENCE = "test-kakao-app-key";

    private final RSAKey signingKey;
    private final RSAKey foreignKey;
    private final HttpServer server;

    private KakaoJwksStub(RSAKey signingKey, RSAKey foreignKey, HttpServer server) {
        this.signingKey = signingKey;
        this.foreignKey = foreignKey;
        this.server = server;
    }

    public static KakaoJwksStub start() {
        try {
            RSAKey signingKey =
                    new RSAKeyGenerator(2048).keyID("kakao-test-key").generate();
            RSAKey foreignKey =
                    new RSAKeyGenerator(2048).keyID("kakao-test-key").generate();
            byte[] jwks = new JWKSet(signingKey.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/.well-known/jwks.json", exchange -> {
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, jwks.length);
                try (OutputStream body = exchange.getResponseBody()) {
                    body.write(jwks);
                }
            });
            server.start();
            return new KakaoJwksStub(signingKey, foreignKey, server);
        } catch (JOSEException | IOException exception) {
            throw new IllegalStateException("Cannot start Kakao JWKS stub", exception);
        }
    }

    public String jwkSetUri() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/.well-known/jwks.json";
    }

    /** 카카오가 발급한 것처럼 유효한 id_token을 만든다. */
    public String idToken(String subject, String nonce) {
        return sign(signingKey, claims(subject, nonce).build());
    }

    /** kid는 같지만 카카오 JWKS에 없는 키로 서명한 위조 id_token을 만든다. */
    public String forgedIdToken(String subject, String nonce) {
        return sign(foreignKey, claims(subject, nonce).build());
    }

    private static JWTClaimsSet.Builder claims(String subject, String nonce) {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .audience(AUDIENCE)
                .subject(subject)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(600)))
                .claim("nonce", nonce);
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
