package com.orbit.auth.adapter.out.apple;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jwt.SignedJWT;

@DisplayName("Apple client_secret 생성기")
class AppleClientSecretFactoryTest {

    private static final Instant NOW = Instant.parse("2026-10-04T01:00:00Z");
    private static final String TEAM_ID = "TEAM123456";
    private static final String KEY_ID = "KEY1234567";
    private static final String BUNDLE_ID = "com.orbit.app";
    private static final Duration TTL = Duration.ofMinutes(10);

    private static KeyPair keyPair;
    private static String pem;

    @BeforeAll
    static void generateKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        keyPair = generator.generateKeyPair();
        pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes())
                        .encodeToString(keyPair.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----";
    }

    @Test
    @DisplayName(".p8 개인키로 ES256 서명한 client_secret을 만들고 kid·iss·sub·aud·iat·exp를 싣는다")
    void createsEs256ClientSecretWithAppleClaims() throws Exception {
        AppleClientSecretFactory factory = factory(pem, Clock.fixed(NOW, ZoneOffset.UTC));

        SignedJWT jwt = SignedJWT.parse(factory.create(BUNDLE_ID));

        assertThat(jwt.verify(new ECDSAVerifier((ECPublicKey) keyPair.getPublic())))
                .isTrue();
        assertThat(jwt.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.ES256);
        assertThat(jwt.getHeader().getKeyID()).isEqualTo(KEY_ID);
        assertThat(jwt.getJWTClaimsSet().getIssuer()).isEqualTo(TEAM_ID);
        assertThat(jwt.getJWTClaimsSet().getSubject()).isEqualTo(BUNDLE_ID);
        assertThat(jwt.getJWTClaimsSet().getAudience()).containsExactly("https://appleid.apple.com");
        assertThat(jwt.getJWTClaimsSet().getIssueTime()).isEqualTo(Date.from(NOW));
        assertThat(jwt.getJWTClaimsSet().getExpirationTime()).isEqualTo(Date.from(NOW.plus(TTL)));
    }

    @Test
    @DisplayName("줄바꿈을 \\n 문자로 적은 환경 변수 형식의 개인키도 읽는다")
    void readsPrivateKeyWithEscapedNewlines() throws Exception {
        AppleClientSecretFactory factory = factory(pem.replace("\n", "\\n"), Clock.fixed(NOW, ZoneOffset.UTC));

        SignedJWT jwt = SignedJWT.parse(factory.create(BUNDLE_ID));

        assertThat(jwt.verify(new ECDSAVerifier((ECPublicKey) keyPair.getPublic())))
                .isTrue();
    }

    @Test
    @DisplayName("만료가 가깝지 않으면 같은 client_secret을 재사용하고 클라이언트별로 따로 만든다")
    void cachesClientSecretPerClientUntilNearExpiry() throws Exception {
        MutableClock clock = new MutableClock(NOW);
        AppleClientSecretFactory factory = factory(pem, clock);
        String first = factory.create(BUNDLE_ID);

        clock.advance(Duration.ofMinutes(5));
        assertThat(factory.create(BUNDLE_ID)).isEqualTo(first);
        assertThat(SignedJWT.parse(factory.create("com.orbit.web"))
                        .getJWTClaimsSet()
                        .getSubject())
                .isEqualTo("com.orbit.web");

        clock.advance(Duration.ofMinutes(4));
        String renewed = factory.create(BUNDLE_ID);
        assertThat(renewed).isNotEqualTo(first);
        assertThat(SignedJWT.parse(renewed).getJWTClaimsSet().getIssueTime()).isEqualTo(Date.from(clock.instant()));
    }

    @Test
    @DisplayName("개인키가 PKCS#8 EC 키가 아니면 만들 때 키 내용을 드러내지 않고 실패한다")
    void failsWithoutLeakingInvalidKey() {
        AppleClientSecretFactory factory = factory("not-a-key-secret-value", Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> factory.create(BUNDLE_ID))
                .isInstanceOf(IllegalStateException.class)
                .message()
                .doesNotContain("not-a-key-secret-value");
    }

    @Test
    @DisplayName("유효 기간은 Apple 상한(15777000초)을 넘을 수 없다")
    void rejectsTtlLongerThanAppleLimit() {
        assertThatThrownBy(() -> new AppleClientSecretProperties(TEAM_ID, KEY_ID, pem, Duration.ofSeconds(15_777_001)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("설정의 문자열 표현에 개인키를 남기지 않는다")
    void masksPrivateKeyInToString() {
        assertThat(new AppleClientSecretProperties(TEAM_ID, KEY_ID, pem, TTL).toString())
                .contains(TEAM_ID)
                .doesNotContain(pem.substring(30, 60));
    }

    private static AppleClientSecretFactory factory(String privateKey, Clock clock) {
        return new AppleClientSecretFactory(new AppleClientSecretProperties(TEAM_ID, KEY_ID, privateKey, TTL), clock);
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
