package com.orbit.auth.application.port.in.command.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Apple 로그인 입력·결과 값의 문자열 표현")
class AppleLoginCommandsTest {

    @Test
    @DisplayName("id_token·authorization code·state·브라우저 연결·교환 코드·code_verifier를 문자열 표현에 남기지 않는다")
    void masksSecretsInToString() {
        assertThat(new LoginWithAppleCommand("secret-id-token", "secret-code").toString())
                .doesNotContain("secret-id-token", "secret-code");
        assertThat(new CompleteAppleWebLoginCommand(
                                "secret-state", "secret-binding", "secret-id-token", "secret-code", null)
                        .toString())
                .doesNotContain("secret-state", "secret-binding", "secret-id-token", "secret-code");
        assertThat(new ExchangeAppleWebLoginCommand("secret-exchange", "secret-verifier").toString())
                .doesNotContain("secret-exchange", "secret-verifier");
        assertThat(new AppleWebLoginStartInfo(
                                URI.create("https://appleid.apple.com"), "secret-binding", Duration.ofMinutes(10))
                        .toString())
                .doesNotContain("secret-binding");
    }
}
