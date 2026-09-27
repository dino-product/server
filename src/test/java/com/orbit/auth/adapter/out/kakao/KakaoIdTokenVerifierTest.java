package com.orbit.auth.adapter.out.kakao;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.orbit.auth.application.port.out.KakaoIdTokenClaims;

@DisplayName("카카오 id_token 검증기")
class KakaoIdTokenVerifierTest {

    private static final String ISSUER = "https://kauth.kakao.com";
    private static final String NATIVE_APP_KEY = "native-app-key";
    private static final String REST_API_KEY = "rest-api-key";
    private static final Instant NOW = Instant.parse("2026-09-27T01:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final KakaoOidcProperties PROPERTIES = new KakaoOidcProperties(
            ISSUER, "https://kauth.kakao.com/.well-known/jwks.json", List.of(NATIVE_APP_KEY, REST_API_KEY));

    private static RSAKey kakaoKey;
    private static RSAKey otherKey;
    private static KakaoIdTokenVerifier verifier;

    @BeforeAll
    static void setUpKeys() throws JOSEException {
        kakaoKey = new RSAKeyGenerator(2048).keyID("kakao-key").generate();
        otherKey = new RSAKeyGenerator(2048).keyID("other-key").generate();
        verifier = new KakaoIdTokenVerifier(
                new ImmutableJWKSet<SecurityContext>(new JWKSet(kakaoKey.toPublicJWK())), PROPERTIES, CLOCK);
    }

    @Test
    @DisplayName("서명·발급자·대상·만료가 유효하면 sub와 nonce를 돌려준다")
    void acceptsValidToken() throws JOSEException {
        String token = sign(kakaoKey, claims().build());

        assertThat(verifier.verify(token)).contains(new KakaoIdTokenClaims("1234567890", "server-nonce"));
    }

    @Test
    @DisplayName("허용 대상 중 하나만 맞아도 통과한다")
    void acceptsAnyAllowedAudience() throws JOSEException {
        String token = sign(kakaoKey, claims().audience(REST_API_KEY).build());

        assertThat(verifier.verify(token)).isPresent();
    }

    @Test
    @DisplayName("다른 키로 서명한 토큰은 거부한다")
    void rejectsForeignSignature() throws JOSEException {
        String token = sign(otherKey, claims().build());

        assertThat(verifier.verify(token)).isEmpty();
    }

    @Test
    @DisplayName("공개키로 HMAC 서명한 알고리즘 혼동 토큰은 거부한다")
    void rejectsAlgorithmConfusion() throws JOSEException {
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.HS256)
                        .keyID(kakaoKey.getKeyID())
                        .build(),
                claims().build());
        jwt.sign(new MACSigner(kakaoKey.toRSAPublicKey().getEncoded()));

        assertThat(verifier.verify(jwt.serialize())).isEmpty();
    }

    @Test
    @DisplayName("서명은 유효해도 발급자가 다르면 거부한다")
    void rejectsWrongIssuer() throws JOSEException {
        String token = sign(kakaoKey, claims().issuer("https://evil.example").build());

        assertThat(verifier.verify(token)).isEmpty();
    }

    @Test
    @DisplayName("서명은 유효해도 대상이 우리 앱 키가 아니면 거부한다")
    void rejectsWrongAudience() throws JOSEException {
        String token = sign(kakaoKey, claims().audience("another-app").build());

        assertThat(verifier.verify(token)).isEmpty();
    }

    @Test
    @DisplayName("서명은 유효해도 만료된 토큰은 거부한다")
    void rejectsExpiredToken() throws JOSEException {
        String token = sign(
                kakaoKey,
                claims().expirationTime(Date.from(NOW.minusSeconds(61))).build());

        assertThat(verifier.verify(token)).isEmpty();
    }

    @Test
    @DisplayName("서명은 유효해도 만료 시각이 없으면 거부한다")
    void rejectsMissingExpiry() throws JOSEException {
        String token = sign(kakaoKey, claims().expirationTime(null).build());

        assertThat(verifier.verify(token)).isEmpty();
    }

    @Test
    @DisplayName("nonce가 없으면 거부한다")
    void rejectsMissingNonce() throws JOSEException {
        String token = sign(kakaoKey, claims().claim("nonce", null).build());

        assertThat(verifier.verify(token)).isEmpty();
    }

    @Test
    @DisplayName("sub가 없으면 거부한다")
    void rejectsMissingSubject() throws JOSEException {
        String token = sign(kakaoKey, claims().subject(null).build());

        assertThat(verifier.verify(token)).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "not-a-jwt", "a.b.c"})
    @DisplayName("JWT 형식이 아니면 거부한다")
    void rejectsMalformedToken(String token) {
        assertThat(verifier.verify(token)).isEmpty();
    }

    private static JWTClaimsSet.Builder claims() {
        return new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .audience(NATIVE_APP_KEY)
                .subject("1234567890")
                .issueTime(Date.from(NOW.minusSeconds(10)))
                .expirationTime(Date.from(NOW.plusSeconds(3600)))
                .claim("nonce", "server-nonce");
    }

    private static String sign(RSAKey key, JWTClaimsSet claims) throws JOSEException {
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }
}
