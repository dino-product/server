package com.orbit.shared.internal.event;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 실패한 이벤트 발행 재전송 설정. 주기와 켜기·끄기는 {@link FailedEventPublicationResubmitScheduler}가 읽는다.
 *
 * @param maxAttempts 최초 처리를 포함한 최대 처리 시도 횟수. 없으면 10, 1 이상
 */
@ConfigurationProperties(prefix = "app.events.resubmission")
public record EventResubmissionProperties(Integer maxAttempts) {

    static final int DEFAULT_MAX_ATTEMPTS = 10;

    public EventResubmissionProperties {
        maxAttempts = maxAttempts == null ? DEFAULT_MAX_ATTEMPTS : maxAttempts;
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("app.events.resubmission.max-attempts must be at least 1");
        }
    }
}
