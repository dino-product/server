package com.orbit.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Access Token 클레임")
class AccessTokenTest {

    private static final AccountId ACCOUNT_ID = new AccountId(1L);
    private static final Instant ISSUED_AT = Instant.parse("2026-09-27T01:00:00Z");
    private static final Instant EXPIRES_AT = ISSUED_AT.plus(Duration.ofHours(1));

    @Test
    @DisplayName("식별자·계정·유효 구간을 보관한다")
    void keepsClaims() {
        AccessToken token = new AccessToken("jti", ACCOUNT_ID, ISSUED_AT, EXPIRES_AT);

        assertThat(token.tokenId()).isEqualTo("jti");
        assertThat(token.accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(token.issuedAt()).isEqualTo(ISSUED_AT);
        assertThat(token.expiresAt()).isEqualTo(EXPIRES_AT);
    }

    @Test
    @DisplayName("남은 유효 시간을 계산하고 만료 후에는 0이다")
    void computesRemainingLifetime() {
        AccessToken token = new AccessToken("jti", ACCOUNT_ID, ISSUED_AT, EXPIRES_AT);

        assertThat(token.remainingLifetimeAt(ISSUED_AT.plus(Duration.ofMinutes(20))))
                .isEqualTo(Duration.ofMinutes(40));
        assertThat(token.remainingLifetimeAt(EXPIRES_AT)).isZero();
        assertThat(token.remainingLifetimeAt(EXPIRES_AT.plusSeconds(1))).isZero();
        assertThatThrownBy(() -> token.remainingLifetimeAt(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("now must not be null");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    @DisplayName("식별자가 비어 있으면 거부한다")
    void rejectsBlankTokenId(String tokenId) {
        assertThatThrownBy(() -> new AccessToken(tokenId, ACCOUNT_ID, ISSUED_AT, EXPIRES_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("tokenId must not be blank");
    }

    @Test
    @DisplayName("계정이 null이면 거부한다")
    void rejectsNullAccountId() {
        assertThatThrownBy(() -> new AccessToken("jti", null, ISSUED_AT, EXPIRES_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("accountId must not be null");
    }

    @Test
    @DisplayName("시각이 null이면 거부한다")
    void rejectsNullInstants() {
        assertThatThrownBy(() -> new AccessToken("jti", ACCOUNT_ID, null, EXPIRES_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("issuedAt and expiresAt must not be null");
        assertThatThrownBy(() -> new AccessToken("jti", ACCOUNT_ID, ISSUED_AT, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("issuedAt and expiresAt must not be null");
    }

    @Test
    @DisplayName("만료가 발급보다 늦지 않으면 거부한다")
    void rejectsNonPositiveLifetime() {
        assertThatThrownBy(() -> new AccessToken("jti", ACCOUNT_ID, ISSUED_AT, ISSUED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("expiresAt must be after issuedAt");
    }
}
