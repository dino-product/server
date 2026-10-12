package com.orbit.shared.internal.event;

import org.springframework.modulith.events.FailedEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;
import org.springframework.stereotype.Component;

/**
 * 실패로 남은 이벤트 발행을 최대 처리 시도 횟수까지 다시 보낸다. 상한에 닿은 발행은 실패 상태로 남긴다. 여러 인스턴스가 함께 돌면 재전송 표시는 같은 발행을 동시에 집는
 * 일만 줄인다. 처리가 시작되거나 다시 실패한 뒤에는 앞서 목록을 읽은 다른 인스턴스가 또 보낼 수 있으므로 리스너는 같은 이벤트를 여러 번 받아도 결과가 같아야 한다.
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
