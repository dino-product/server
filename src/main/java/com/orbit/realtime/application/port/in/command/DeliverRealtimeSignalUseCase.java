package com.orbit.realtime.application.port.in.command;

import com.orbit.realtime.domain.RealtimeSignal;

/** 다른 인스턴스(자신 포함)가 알린 신호를 이 인스턴스의 연결로 보낸다. */
public interface DeliverRealtimeSignalUseCase {

    void deliver(RealtimeSignal signal);
}
