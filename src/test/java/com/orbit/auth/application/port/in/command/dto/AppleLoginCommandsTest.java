package com.orbit.auth.application.port.in.command.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Apple 로그인 입력 값의 문자열 표현")
class AppleLoginCommandsTest {

    @Test
    @DisplayName("id_token·authorization code·state·브라우저 연결을 문자열 표현에 남기지 않는다")
    void masksSecretsInToString() {
        assertThat(new LoginWithAppleCommand("secret-id-token", "secret-code").toString())
                .doesNotContain("secret-id-token", "secret-code");
        assertThat(new CompleteAppleWebLoginCommand(
                                "secret-state", "secret-binding", "secret-id-token", "secret-code", null)
                        .toString())
                .doesNotContain("secret-state", "secret-binding", "secret-id-token", "secret-code");
    }
}
