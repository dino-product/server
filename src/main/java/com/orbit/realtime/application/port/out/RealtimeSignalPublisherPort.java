package com.orbit.realtime.application.port.out;

import com.orbit.realtime.domain.RealtimeSignal;

/** 신호를 모든 인스턴스가 받는 채널로 보낸다. 받지 못한 인스턴스에 다시 보내지 않으며, 실패는 호출한 쪽으로 던지지 않고 구현이 기록한다. */
public interface RealtimeSignalPublisherPort {

    void publish(RealtimeSignal signal);
}
