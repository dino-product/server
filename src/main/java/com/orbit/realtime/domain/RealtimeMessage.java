package com.orbit.realtime.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 연결된 화면에 보내는 이벤트. {@link #name()}은 화면이 구분하는 이벤트 이름이고, 담는 값은 화면이 다음에 할 일을 정하는 데 필요한 것뿐이다. 바뀐 내용은 담지
 * 않으며 화면이 다시 조회한다.
 */
public sealed interface RealtimeMessage {

    String name();

    /** 연결을 등록했다. 이 이벤트 뒤에 일어난 변경부터 신호를 받는다. */
    record Connected() implements RealtimeMessage {

        @Override
        public String name() {
            return "connected";
        }
    }

    /** 서버가 연결을 닫으니 잠시 뒤 다시 연결해야 한다(연결 수명 만료, 서버 종료). */
    record Reconnect() implements RealtimeMessage {

        @Override
        public String name() {
            return "reconnect";
        }
    }

    /** 연결한 발주사의 작업을 저장했다. 화면은 보고 있는 작업 목록을 다시 조회한다. {@code workIds}의 순서에는 의미가 없다. */
    record WorkChanged(List<Long> workIds, Instant occurredAt) implements RealtimeMessage {

        public WorkChanged {
            workIds = List.copyOf(Objects.requireNonNull(workIds, "workIds must not be null"));
        }

        @Override
        public String name() {
            return "work-changed";
        }
    }

    /** 연결한 계정의 알림센터에 알림이 생겼다(발주사와 관계없이 계정 단위). 화면은 안 읽은 수를 다시 조회한다. */
    record NotificationCreated(Instant occurredAt) implements RealtimeMessage {

        @Override
        public String name() {
            return "notification-created";
        }
    }

    /** 연결한 계정이 다른 화면에서 알림을 읽었다. 화면은 안 읽은 수를 다시 조회한다. */
    record NotificationRead(Instant occurredAt) implements RealtimeMessage {

        @Override
        public String name() {
            return "notification-read";
        }
    }

    /** 서버가 연결을 닫았고 다시 연결하지 않아야 한다. 사유 {@code replaced}는 같은 계정이 더 연결해 계정 상한을 넘어 가장 오래된 연결을 닫았다는 뜻이다. */
    record Closed(String reason) implements RealtimeMessage {

        public static final String REPLACED = "replaced";

        @Override
        public String name() {
            return "closed";
        }
    }
}
