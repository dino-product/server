package com.orbit.auth.application.port.in.command.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("카카오 웹 로그인 입력 값의 문자열 표현")
class KakaoLoginCommandsTest {

    @Test
    @DisplayName("state·브라우저 연결·인가 코드를 문자열 표현에 남기지 않는다")
    void masksSecretsInToString() {
        assertThat(new CompleteKakaoWebLoginCommand("secret-state", "secret-binding", "secret-code", "access_denied")
                        .toString())
                .doesNotContain("secret-state", "secret-binding", "secret-code")
                .contains("access_denied");
    }
}
