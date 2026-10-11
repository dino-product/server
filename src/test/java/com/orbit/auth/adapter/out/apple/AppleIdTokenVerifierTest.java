package com.orbit.auth.adapter.out.apple;

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
import com.orbit.auth.application.port.out.AppleIdTokenClaims;

@DisplayName("Apple id_token 검증기")
class AppleIdTokenVerifierTest {

    private static final String ISSUER = "https://appleid.apple.com";
    private static final String BUNDLE_ID = "com.orbit.app";
    private static final String SERVICES_ID = "com.orbit.web";
    private static final String SUBJECT = "001234.0123456789abcdef0123456789abcdef.0123";
    private static final String HASHED_NONCE = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
    private static final Instant NOW = Instant.parse("2026-10-04T01:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final AppleOidcProperties PROPERTIES =
            new AppleOidcProperties(ISSUER, "https://appleid.apple.com/auth/keys", List.of(BUNDLE_ID, SERVICES_ID));

    private static RSAKey appleKey;
    private static RSAKey otherKey;
    private static AppleIdTokenVerifier verifier;

    @BeforeAll
    static void setUpKeys() throws JOSEException {
        appleKey = new RSAKeyGenerator(2048).keyID("apple-key").generate();
        otherKey = new RSAKeyGenerator(2048).keyID("apple-key").generate();
        verifier = new AppleIdTokenVerifier(
                new ImmutableJWKSet<SecurityContext>(new JWKSet(appleKey.toPublicJWK())), PROPERTIES, CLOCK);
    }

    @Test
    @DisplayName("서명·발급자·대상·만료가 유효하면 sub와 nonce 클레임을 돌려준다")
    void acceptsValidToken() throws JOSEException {
        String token = sign(appleKey, claims().build());

        assertThat(verifier.verify(token)).contains(new AppleIdTokenClaims(SUBJECT, HASHED_NONCE, BUNDLE_ID));
    }

    @Test
    @DisplayName("허용 목록의 다른 클라이언트(Services ID) 대상도 통과하고 그 클라이언트를 돌려준다")
    void acceptsAnyAllowedAudience() throws JOSEException {
        String token = sign(appleKey, claims().audience(SERVICES_ID).build());

        assertThat(verifier.verify(token)).map(AppleIdTokenClaims::clientId).contains(SERVICES_ID);
    }

    @Test
    @DisplayName("JWKS에 없는 키로 서명한 토큰은 거부한다")
    void rejectsForeignSignature() throws JOSEException {
        String token = sign(otherKey, claims().build());

        assertThat(verifier.verify(token)).isEmpty();
    }

    @Test
    @DisplayName("공개키로 HMAC 서명한 알고리즘 혼동 토큰은 거부한다")
    void rejectsAlgorithmConfusion() throws JOSEException {
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.HS256)
                        .keyID(appleKey.getKeyID())
                        .build(),
                claims().build());
        jwt.sign(new MACSigner(appleKey.toRSAPublicKey().getEncoded()));

        assertThat(verifier.verify(jwt.serialize())).isEmpty();
    }

    @Test
    @DisplayName("서명은 유효해도 발급자가 Apple이 아니면 거부한다")
    void rejectsWrongIssuer() throws JOSEException {
        String token = sign(appleKey, claims().issuer("https://kauth.kakao.com").build());

        assertThat(verifier.verify(token)).isEmpty();
    }

    @Test
    @DisplayName("서명은 유효해도 대상이 허용 목록에 없으면 거부한다")
    void rejectsWrongAudience() throws JOSEException {
        String token = sign(appleKey, claims().audience("com.other.app").build());

        assertThat(verifier.verify(token)).isEmpty();
    }

    @Test
    @DisplayName("서명은 유효해도 만료된 토큰은 거부한다")
    void rejectsExpiredToken() throws JOSEException {
        String token = sign(
                appleKey,
                claims().expirationTime(Date.from(NOW.minusSeconds(61))).build());

        assertThat(verifier.verify(token)).isEmpty();
    }

    @Test
    @DisplayName("서명은 유효해도 만료 시각이 없으면 거부한다")
    void rejectsMissingExpiry() throws JOSEException {
        String token = sign(appleKey, claims().expirationTime(null).build());

        assertThat(verifier.verify(token)).isEmpty();
    }

    @Test
    @DisplayName("nonce가 없으면 거부한다")
    void rejectsMissingNonce() throws JOSEException {
        String token = sign(appleKey, claims().claim("nonce", null).build());

        assertThat(verifier.verify(token)).isEmpty();
    }

    @Test
    @DisplayName("sub가 없으면 거부한다")
    void rejectsMissingSubject() throws JOSEException {
        String token = sign(appleKey, claims().subject(null).build());

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
                .audience(BUNDLE_ID)
                .subject(SUBJECT)
                .issueTime(Date.from(NOW.minusSeconds(10)))
                .expirationTime(Date.from(NOW.plusSeconds(600)))
                .claim("nonce", HASHED_NONCE)
                .claim("nonce_supported", true);
    }

    private static String sign(RSAKey key, JWTClaimsSet claims) throws JOSEException {
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }
}
