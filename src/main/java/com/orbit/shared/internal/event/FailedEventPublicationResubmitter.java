package com.orbit.shared.internal.event;

import org.springframework.modulith.events.FailedEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;
import org.springframework.stereotype.Component;

/**
 * 실패로 남은 이벤트 발행을 최대 처리 시도 횟수까지 다시 보낸다. 여러 인스턴스가 함께 불러도 한 발행은 먼저 재전송 상태로 바꾼 인스턴스만 보낸다. 상한에 닿은 발행은 실패
 * 상태로 남긴다.
 */
@Component
class FailedEventPublicationResubmitter {

    private final FailedEventPublications failedPublications;
    private final EventResubmissionProperties properties;

    FailedEventPublicationResubmitter(
            FailedEventPublications failedPublications, EventResubmissionProperties properties) {
        this.failedPublications = failedPublications;
        this.properties = properties;
    }

    void resubmitFailed() {
        // 실패 발행은 오래된 순으로 읽혀 개수를 제한하면 상한에 닿은 발행이 앞자리를 채워 새 실패가 밀려난다. 모두 읽은 뒤 시도 횟수로 거른다.
        failedPublications.resubmit(ResubmissionOptions.defaults()
                .withBatchSize(Integer.MAX_VALUE)
                .withFilter(publication -> publication.getCompletionAttempts() < properties.maxAttempts()));
    }
}
