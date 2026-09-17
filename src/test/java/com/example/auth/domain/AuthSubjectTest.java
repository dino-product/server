package com.example.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AuthSubjectTest {
    @Test
    void usesUserNamespaceForSubject() {
        assertThat(new AuthSubject(7L).value()).isEqualTo("user:7");
    }

    @Test
    void rejectsInvalidIdentifier() {
        assertThatThrownBy(() -> new AuthSubject(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AuthSubject(0L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AuthSubject(-1L)).isInstanceOf(IllegalArgumentException.class);
    }
}
