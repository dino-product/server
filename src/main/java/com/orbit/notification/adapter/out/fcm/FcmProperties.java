package com.orbit.notification.adapter.out.fcm;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 기사 앱 푸시 제공자 FCM(HTTP v1) 설정. 자격 증명은 Firebase 서비스 계정 키(JSON)를 base64로 인코딩한 값이며 켰을 때만 필요하다. 테스트는 주소만 로컬
 * 스텁으로 바꾼다. 제한 시간은 토큰 발급과 발신 호출의 연결·읽기에 각각 적용한다.
 */
@ConfigurationProperties(prefix = "app.notification.push.fcm")
public record FcmProperties(boolean enabled, String credentials, String apiUri, Duration timeout) {

    public FcmProperties {
        if (enabled && (credentials == null || credentials.isBlank())) {
            throw new IllegalArgumentException("app.notification.push.fcm.credentials must not be blank when enabled");
        }
        if (apiUri == null || apiUri.isBlank()) {
            throw new IllegalArgumentException("app.notification.push.fcm.api-uri must not be blank");
        }
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("app.notification.push.fcm.timeout must be positive");
        }
    }

    @Override
    public String toString() {
        return "FcmProperties[enabled=" + enabled + ", credentials=***, apiUri=" + apiUri + ", timeout=" + timeout
                + "]";
    }
}
