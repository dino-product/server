package com.orbit.realtime.application.port.out;

import com.orbit.realtime.application.port.in.command.RealtimeChannel;
import com.orbit.realtime.domain.ClientType;

/** 이 서버 인스턴스에 열린 실시간 연결 목록. 연결은 인스턴스 메모리에만 있고 DB·Redis에 남기지 않는다. */
public interface RealtimeConnectionPort {

    /**
     * 연결을 등록하고 {@code connected}를 보낸다. 같은 계정의 연결이 계정 상한을 넘으면 그 계정의 가장 오래된 연결을 닫는다. 인스턴스 연결 수 상한에 닿았거나
     * 인스턴스가 종료 중이면 등록하지 않고 {@code false}를 돌려준다.
     */
    boolean register(Long accountId, Long organizationId, ClientType clientType, RealtimeChannel channel);
}
