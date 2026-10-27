package com.orbit.realtime.application.port.in.command;

import com.orbit.realtime.domain.RealtimeSignal;

/** 커밋된 변경을 모든 인스턴스로 알린다. 신호는 식별자·시각만 담은 값이라 Command로 감싸지 않고 그대로 받는다. */
public interface PublishRealtimeSignalUseCase {

    void publish(RealtimeSignal signal);
}
