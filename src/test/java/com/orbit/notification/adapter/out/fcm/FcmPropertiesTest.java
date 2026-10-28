package com.orbit.notification.adapter.out.fcm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("FCM 설정")
class FcmPropertiesTest {

    private static final String API_URI = "https://fcm.googleapis.com";
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    @Test
    @DisplayName("켰는데 자격 증명이 없으면 기동하지 않고, 끄면 자격 증명 없이 기동한다")
    void requiresCredentialsOnlyWhenEnabled() {
        assertThatThrownBy(() -> new FcmProperties(true, " ", API_URI, TIMEOUT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("credentials");
        assertThatThrownBy(() -> new FcmProperties(true, null, API_URI, TIMEOUT))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(new FcmProperties(false, "", API_URI, TIMEOUT).enabled()).isFalse();
    }

    @Test
    @DisplayName("주소가 비었거나 제한 시간이 양수가 아니면 기동하지 않는다")
    void rejectsMissingUriOrNonPositiveTimeout() {
        assertThatThrownBy(() -> new FcmProperties(false, "", " ", TIMEOUT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FcmProperties(false, "", API_URI, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FcmProperties(false, "", API_URI, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("문자열 표현에 자격 증명을 남기지 않는다")
    void masksCredentialsInStringForm() {
        assertThat(new FcmProperties(true, "SECRET-CREDENTIALS", API_URI, TIMEOUT).toString())
                .doesNotContain("SECRET")
                .contains(API_URI);
    }
}
