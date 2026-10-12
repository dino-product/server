package com.orbit.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("PKCE code_challenge")
class PkceChallengeTest {

    // RFC 7636 부록 B의 예시 값
    private static final String VERIFIER = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
    private static final String CHALLENGE = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";

    @Test
    @DisplayName("code_verifier의 SHA-256 base64url이 challenge와 같으면 맞는다(S256)")
    void matchesVerifierWithS256() {
        assertThat(new PkceChallenge(CHALLENGE).matches(VERIFIER)).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(
            strings = {
                "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXX",
                "short",
                "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"
            })
    @DisplayName("다른 verifier·형식이 아닌 verifier·challenge 자체는 맞지 않는다")
    void rejectsOtherVerifiers(String verifier) {
        assertThat(new PkceChallenge(CHALLENGE).matches(verifier)).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(
            strings = {
                "plain-text-challenge",
                "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-c=",
                "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cMx"
            })
    @DisplayName("S256 challenge(base64url 43자)가 아니면 만들 수 없다")
    void rejectsInvalidChallenge(String challenge) {
        assertThat(PkceChallenge.parse(challenge)).isEmpty();
        assertThatThrownBy(() -> new PkceChallenge(challenge)).isInstanceOf(IllegalArgumentException.class);
    }
}
