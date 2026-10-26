package com.orbit.auth.application.port.in.command.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orbit.auth.domain.OAuthProvider;

@DisplayName("웹 로그인 입력·결과 값의 문자열 표현")
class WebLoginCommandsTest {

    @Test
    @DisplayName("브라우저 연결·교환 코드·code_verifier를 문자열 표현에 남기지 않는다")
    void masksSecretsInToString() {
        assertThat(new ExchangeWebLoginCommand(OAuthProvider.APPLE, "secret-exchange", "secret-verifier").toString())
                .doesNotContain("secret-exchange", "secret-verifier");
        assertThat(new WebLoginStartInfo(
                                URI.create("https://appleid.apple.com"), "secret-binding", Duration.ofMinutes(10))
                        .toString())
                .doesNotContain("secret-binding");
    }
}
