package com.orbit.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("해시 nonce")
class HashedNonceTest {

    private static final String ABC_SHA256 = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    @Test
    @DisplayName("raw nonce의 UTF-8 SHA-256을 소문자 hex로 만든다")
    void hashesRawNonceAsLowercaseHex() {
        assertThat(HashedNonce.fromRaw("abc").value()).isEqualTo(ABC_SHA256);
    }

    @Test
    @DisplayName("id_token nonce 클레임의 hex는 대소문자를 구분하지 않고 같은 해시로 읽는다")
    void readsClaimCaseInsensitively() {
        assertThat(HashedNonce.fromClaim(ABC_SHA256.toUpperCase())).contains(new HashedNonce(ABC_SHA256));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(
            strings = {
                "Zm9vYmFyYmF6cXV4Zm9vYmFyYmF6cXV4Zm9vYmFyYmF6",
                "abc",
                "zz7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
            })
    @DisplayName("해시 형식이 아닌 클레임(raw nonce 그대로 등)은 해시로 읽지 않는다")
    void rejectsClaimThatIsNotHash(String claim) {
        assertThat(HashedNonce.fromClaim(claim)).isEmpty();
    }

    @Test
    @DisplayName("소문자 hex 64자가 아니면 만들 수 없다")
    void rejectsInvalidValue() {
        assertThatThrownBy(() -> new HashedNonce(ABC_SHA256.toUpperCase()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HashedNonce(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("raw nonce가 비어 있으면 해시할 수 없다")
    void rejectsBlankRawNonce() {
        assertThatThrownBy(() -> HashedNonce.fromRaw(" ")).isInstanceOf(IllegalArgumentException.class);
    }
}
