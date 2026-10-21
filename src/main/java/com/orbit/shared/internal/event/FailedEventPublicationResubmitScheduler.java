package com.orbit.shared.internal.event;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 실패한 이벤트 발행 재전송을 주기적으로 실행한다. 앞선 실행이 끝난 뒤 간격을 두고 다시 돈다. */
@Component
@ConditionalOnProperty(prefix = "app.events.resubmission", name = "enabled", havingValue = "true")
class FailedEventPublicationResubmitScheduler {

    private final FailedEventPublicationResubmitter resubmitter;

    FailedEventPublicationResubmitScheduler(FailedEventPublicationResubmitter resubmitter) {
        this.resubmitter = resubmitter;
    }

    @Scheduled(
            fixedDelayString = "${app.events.resubmission.interval}",
            initialDelayString = "${app.events.resubmission.interval}")
    void resubmitFailed() {
        resubmitter.resubmitFailed();
    }
}
