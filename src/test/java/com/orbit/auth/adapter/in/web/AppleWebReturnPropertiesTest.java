package com.orbit.auth.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Apple 웹 로그인 복귀 주소 설정")
class AppleWebReturnPropertiesTest {

    @Test
    @DisplayName("등록한 클라이언트 이름은 대소문자를 구분하지 않고 찾고, 등록하지 않은 이름은 찾지 않는다")
    void resolvesRegisteredClientsOnly() {
        AppleWebReturnProperties properties = new AppleWebReturnProperties(
                Map.of("Web", "https://admin.example.com/login/apple", "android", "orbit://auth/apple"));

        assertThat(properties.returnUri("web")).contains("https://admin.example.com/login/apple");
        assertThat(properties.returnUri("ANDROID")).contains("orbit://auth/apple");
        assertThat(properties.returnUri("https://evil.example")).isEmpty();
        assertThat(properties.returnUri(null)).isEmpty();
    }

    @Test
    @DisplayName("복귀 주소가 없거나 절대 주소가 아니면 기동하지 않는다")
    void requiresAbsoluteReturnUris() {
        assertThatThrownBy(() -> new AppleWebReturnProperties(Map.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AppleWebReturnProperties(Map.of("web", "/login/apple")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AppleWebReturnProperties(Map.of("web", " ")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
