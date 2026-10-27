package com.orbit.realtime.application.port.in.command.dto;

import java.util.Objects;

import com.orbit.realtime.application.port.in.command.RealtimeChannel;
import com.orbit.realtime.domain.ClientType;

/** 요청 계정이 발주사의 실시간 신호를 받으려고 연 연결. */
public record OpenRealtimeStreamCommand(
        Long accountId, Long organizationId, ClientType clientType, RealtimeChannel channel) {

    public OpenRealtimeStreamCommand {
        Objects.requireNonNull(accountId, "accountId must not be null");
        Objects.requireNonNull(organizationId, "organizationId must not be null");
        Objects.requireNonNull(clientType, "clientType must not be null");
        Objects.requireNonNull(channel, "channel must not be null");
    }
}
