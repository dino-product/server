package com.orbit.auth.adapter.out.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.orbit.auth.application.port.out.IssuedAccessToken;
import com.orbit.auth.domain.AccountId;

@DisplayName("JWT Access Token 어댑터")
class JwtAccessTokenAdapterTest {

    private static final String SECRET = "test-secret-key-with-at-least-32-bytes-of-entropy";
    private static final String ISSUER = "orbit-test";
    private static final Duration TTL = Duration.ofHours(1);
    private static final Instant NOW = Instant.parse("2026-09-27T01:00:00Z");
    private static final AccountId ACCOUNT_ID = new AccountId(42L);
    private static final JwtProperties PROPERTIES = new JwtProperties(SECRET, ISSUER, TTL);

    private final JwtAccessTokenAdapter adapter = new JwtAccessTokenAdapter(PROPERTIES, fixedClock(NOW));

    @Test
    @DisplayName("발급한 토큰을 같은 클레임으로 다시 읽는다")
    void issuesAndParsesToken() {
        IssuedAccessToken issued = adapter.issue(ACCOUNT_ID);

        assertThat(issued.token().accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(issued.token().issuedAt()).isEqualTo(NOW);
        assertThat(issued.token().expiresAt()).isEqualTo(NOW.plus(TTL));
        assertThat(adapter.parse(issued.value())).contains(issued.token());
    }

    @Test
    @DisplayName("발급 시각을 초 단위로 맞춰 토큰 클레임과 같게 한다")
    void truncatesIssuedAtToSeconds() {
        JwtAccessTokenAdapter fractional = new JwtAccessTokenAdapter(PROPERTIES, fixedClock(NOW.plusMillis(567)));

        IssuedAccessToken issued = fractional.issue(ACCOUNT_ID);

        assertThat(issued.token().issuedAt()).isEqualTo(NOW);
        assertThat(issued.token().expiresAt()).isEqualTo(NOW.plus(TTL));
        assertThat(fractional.parse(issued.value())).contains(issued.token());
    }

    @Test
    @DisplayName("발급마다 다른 식별자를 가진다")
    void issuesUniqueTokenIds() {
        assertThat(adapter.issue(ACCOUNT_ID).token().tokenId())
                .isNotEqualTo(adapter.issue(ACCOUNT_ID).token().tokenId());
    }

    @Test
    @DisplayName("본문이 바뀐 토큰은 거부한다")
    void rejectsTamperedToken() {
        String value = adapter.issue(ACCOUNT_ID).value();
        String[] parts = value.split("\\.");
        String tampered = parts[0] + "." + parts[1].substring(0, parts[1].length() - 2) + "AA." + parts[2];

        assertThat(adapter.parse(tampered)).isEmpty();
    }

    @Test
    @DisplayName("다른 비밀키로 서명한 토큰은 거부한다")
    void rejectsForeignSecret() {
        JwtAccessTokenAdapter other = new JwtAccessTokenAdapter(
                new JwtProperties("another-secret-key-with-at-least-32-bytes!!", ISSUER, TTL), fixedClock(NOW));

        assertThat(adapter.parse(other.issue(ACCOUNT_ID).value())).isEmpty();
    }

    @Test
    @DisplayName("만료 시각이 지나면 오차 없이 거부한다")
    void rejectsExpiredToken() {
        String value = adapter.issue(ACCOUNT_ID).value();
        JwtAccessTokenAdapter later =
                new JwtAccessTokenAdapter(PROPERTIES, fixedClock(NOW.plus(TTL).plusSeconds(1)));
        JwtAccessTokenAdapter atExpiry = new JwtAccessTokenAdapter(PROPERTIES, fixedClock(NOW.plus(TTL)));

        assertThat(later.parse(value)).isEmpty();
        assertThat(atExpiry.parse(value)).isEmpty();
    }

    @Test
    @DisplayName("서명은 유효해도 발급자가 다르면 거부한다")
    void rejectsWrongIssuer() {
        JwtAccessTokenAdapter otherIssuer =
                new JwtAccessTokenAdapter(new JwtProperties(SECRET, "someone-else", TTL), fixedClock(NOW));

        assertThat(adapter.parse(otherIssuer.issue(ACCOUNT_ID).value())).isEmpty();
    }

    @Test
    @DisplayName("서명은 유효해도 Access Token 용도가 아니면 거부한다")
    void rejectsOtherTokenUse() throws JOSEException {
        String refreshLike = sign(claims().claim(JwtAccessTokenAdapter.TOKEN_USE_CLAIM, "refresh"));
        String missingUse = sign(claims().claim(JwtAccessTokenAdapter.TOKEN_USE_CLAIM, null));

        assertThat(adapter.parse(refreshLike)).isEmpty();
        assertThat(adapter.parse(missingUse)).isEmpty();
    }

    @Test
    @DisplayName("서명은 유효해도 계정·식별자 클레임이 어긋나면 거부한다")
    void rejectsInvalidSubjectOrId() throws JOSEException {
        assertThat(adapter.parse(sign(claims().subject("not-a-number")))).isEmpty();
        assertThat(adapter.parse(sign(claims().subject("0")))).isEmpty();
        assertThat(adapter.parse(sign(claims().jwtID(null)))).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "not-a-jwt"})
    @DisplayName("JWT 형식이 아니면 거부한다")
    void rejectsMalformedToken(String value) {
        assertThat(adapter.parse(value)).isEmpty();
    }

    @Test
    @DisplayName("32바이트 미만 비밀키 설정은 거부한다")
    void rejectsShortSecret() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new JwtProperties("short", ISSUER, TTL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("app.auth.jwt.secret must be at least 32 bytes");
    }

    private static Clock fixedClock(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }

    private static JWTClaimsSet.Builder claims() {
        return new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject("42")
                .jwtID("jti")
                .issueTime(Date.from(NOW))
                .expirationTime(Date.from(NOW.plus(TTL)))
                .claim(JwtAccessTokenAdapter.TOKEN_USE_CLAIM, JwtAccessTokenAdapter.ACCESS_TOKEN_USE);
    }

    private static String sign(JWTClaimsSet.Builder claims) throws JOSEException {
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims.build());
        jwt.sign(new MACSigner(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        return jwt.serialize();
    }
}
