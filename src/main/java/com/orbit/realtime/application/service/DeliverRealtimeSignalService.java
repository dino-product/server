package com.orbit.realtime.application.service;

import org.springframework.stereotype.Service;

import com.orbit.realtime.application.port.in.command.DeliverRealtimeSignalUseCase;
import com.orbit.realtime.application.port.out.RealtimeConnectionPort;
import com.orbit.realtime.domain.RealtimeSignal;
import com.orbit.realtime.domain.RealtimeSignal.AccountNotificationCreated;
import com.orbit.realtime.domain.RealtimeSignal.AccountNotificationRead;
import com.orbit.realtime.domain.RealtimeSignal.OrganizationWorksChanged;

/**
 * 받은 신호를 이 인스턴스의 연결로 보낸다. 작업 신호는 그 발주사로 연 연결에, 알림 신호는 알림센터가 계정 단위이므로 그 계정의 모든 연결에 보낸다. 트랜잭션을 두지
 * 않는다(메모리의 연결 목록만 쓴다).
 */
@Service
public class DeliverRealtimeSignalService implements DeliverRealtimeSignalUseCase {

    private final RealtimeConnectionPort connectionPort;

    public DeliverRealtimeSignalService(RealtimeConnectionPort connectionPort) {
        this.connectionPort = connectionPort;
    }

    @Override
    public void deliver(RealtimeSignal signal) {
        switch (signal) {
            case OrganizationWorksChanged works ->
                connectionPort.sendToOrganization(works.organizationId(), works.message());
            case AccountNotificationCreated created ->
                connectionPort.sendToAccount(created.accountId(), created.message());
            case AccountNotificationRead read -> connectionPort.sendToAccount(read.accountId(), read.message());
        }
    }
}
