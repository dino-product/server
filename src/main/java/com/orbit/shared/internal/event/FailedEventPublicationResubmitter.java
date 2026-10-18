package com.orbit.shared.internal.event;

import java.time.Clock;
import java.time.Instant;

import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.FailedEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;
import org.springframework.stereotype.Component;

/**
 * 실패로 남은 이벤트 발행을 처리될 때까지 횟수 제한 없이 다시 보낸다. 마지막 시도 뒤 {@link EventResubmissionProperties#backoffAfter}만큼 지난 발행만
 * 보내, 계속 실패하는 발행이 주기마다 리스너를 다시 돌리지 않게 한다. 여러 인스턴스가 함께 돌면 재전송 표시는 같은 발행을 동시에 집는 일만 줄인다. 처리가 시작되거나 다시
 * 실패한 뒤에는 앞서 목록을 읽은 다른 인스턴스가 또 보낼 수 있으므로 리스너는 같은 이벤트를 여러 번 받아도 결과가 같아야 한다.
 */
@Component
class FailedEventPublicationResubmitter {

    private final FailedEventPublications failedPublications;
    private final EventResubmissionProperties properties;
    private final Clock clock;

    FailedEventPublicationResubmitter(
            FailedEventPublications failedPublications, EventResubmissionProperties properties, Clock clock) {
        this.failedPublications = failedPublications;
        this.properties = properties;
        this.clock = clock;
    }

    void resubmitFailed() {
        Instant now = clock.instant();
        // 실패 발행은 오래된 순으로 읽혀 개수를 제한하면 대기 중인 발행이 앞자리를 채워 보낼 때가 된 발행이 밀려난다. 모두 읽은 뒤 대기 시간으로 거른다.
        failedPublications.resubmit(ResubmissionOptions.defaults()
                .withBatchSize(Integer.MAX_VALUE)
                .withFilter(publication -> isDue(publication, now)));
    }

    private boolean isDue(EventPublication publication, Instant now) {
        Instant lastAttempt = publication.getLastResubmissionDate() == null
                ? publication.getPublicationDate()
                : publication.getLastResubmissionDate();
        return !now.isBefore(lastAttempt.plus(properties.backoffAfter(publication.getCompletionAttempts())));
    }
}
