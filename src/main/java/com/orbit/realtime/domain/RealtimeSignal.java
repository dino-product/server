package com.orbit.realtime.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 인스턴스 사이로 보내는 변경 신호. 누구의 연결로 보낼지(발주사 또는 계정)와 그 연결에 보낼 이벤트를 정한다. 모든 인스턴스가 받아 자기 메모리의 연결에 전달한다.
 */
public sealed interface RealtimeSignal {

    RealtimeMessage message();

    /** 발주사의 작업을 저장했다. 그 발주사에 연결한 화면으로 보낸다. */
    record OrganizationWorksChanged(Long organizationId, List<Long> workIds, Instant occurredAt)
            implements RealtimeSignal {

        public OrganizationWorksChanged {
            Objects.requireNonNull(organizationId, "organizationId must not be null");
            workIds = List.copyOf(Objects.requireNonNull(workIds, "workIds must not be null"));
            Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        }

        @Override
        public RealtimeMessage message() {
            return new RealtimeMessage.WorkChanged(workIds, occurredAt);
        }
    }

    /** 계정의 알림센터에 알림이 생겼다. 그 계정의 모든 연결로 보낸다(알림센터가 계정 단위). */
    record AccountNotificationCreated(Long accountId, Instant occurredAt) implements RealtimeSignal {

        public AccountNotificationCreated {
            Objects.requireNonNull(accountId, "accountId must not be null");
            Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        }

        @Override
        public RealtimeMessage message() {
            return new RealtimeMessage.NotificationCreated(occurredAt);
        }
    }

    /** 계정이 알림을 읽었다. 그 계정의 모든 연결로 보낸다. */
    record AccountNotificationRead(Long accountId, Instant occurredAt) implements RealtimeSignal {

        public AccountNotificationRead {
            Objects.requireNonNull(accountId, "accountId must not be null");
            Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        }

        @Override
        public RealtimeMessage message() {
            return new RealtimeMessage.NotificationRead(occurredAt);
        }
    }
}
