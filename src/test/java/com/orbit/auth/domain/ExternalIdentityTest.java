package com.orbit.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("외부 식별")
class ExternalIdentityTest {

    @Test
    @DisplayName("제공자와 subject를 그대로 보관한다")
    void keepsProviderAndSubject() {
        ExternalIdentity identity = new ExternalIdentity(OAuthProvider.KAKAO, "1234567890");

        assertThat(identity.provider()).isEqualTo(OAuthProvider.KAKAO);
        assertThat(identity.subject()).isEqualTo("1234567890");
    }

    @Test
    @DisplayName("같은 제공자와 subject면 같은 식별이다")
    void equalsBySubjectWithinProvider() {
        assertThat(new ExternalIdentity(OAuthProvider.KAKAO, "1"))
                .isEqualTo(new ExternalIdentity(OAuthProvider.KAKAO, "1"));
    }

    @Test
    @DisplayName("제공자가 null이면 거부한다")
    void rejectsNullProvider() {
        assertThatThrownBy(() -> new ExternalIdentity(null, "1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("provider must not be null");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    @DisplayName("subject가 비어 있으면 거부한다")
    void rejectsBlankSubject(String subject) {
        assertThatThrownBy(() -> new ExternalIdentity(OAuthProvider.KAKAO, subject))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("subject must not be blank");
    }

    @Test
    @DisplayName("subject가 64자를 넘으면 거부한다")
    void rejectsTooLongSubject() {
        assertThatThrownBy(() -> new ExternalIdentity(OAuthProvider.KAKAO, "x".repeat(65)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("subject must be at most 64 characters");
    }
}
