package com.orbit.realtime.application.service;

import org.springframework.stereotype.Service;

import com.orbit.realtime.application.port.in.command.PublishRealtimeSignalUseCase;
import com.orbit.realtime.application.port.out.RealtimeSignalPublisherPort;
import com.orbit.realtime.domain.RealtimeSignal;

/**
 * 커밋된 변경 신호를 모든 인스턴스로 알린다. 트랜잭션을 두지 않는다. DB를 쓰지 않고 채널로 보내기만 하며, 보내지 못한 신호는 다시 보내지 않는다. 화면은 다시
 * 연결한 뒤와 주기 조회 때 다시 조회해 놓친 변경을 맞춘다.
 */
@Service
public class PublishRealtimeSignalService implements PublishRealtimeSignalUseCase {

    private final RealtimeSignalPublisherPort publisherPort;

    public PublishRealtimeSignalService(RealtimeSignalPublisherPort publisherPort) {
        this.publisherPort = publisherPort;
    }

    @Override
    public void publish(RealtimeSignal signal) {
        publisherPort.publish(signal);
    }
}
