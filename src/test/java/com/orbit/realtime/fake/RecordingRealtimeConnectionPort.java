package com.orbit.realtime.fake;

import java.util.ArrayList;
import java.util.List;

import com.orbit.realtime.application.port.in.command.RealtimeChannel;
import com.orbit.realtime.application.port.out.RealtimeConnectionPort;
import com.orbit.realtime.domain.ClientType;
import com.orbit.realtime.domain.RealtimeMessage;

/** 등록한 계정과 보낸 이벤트를 기록하는 연결 목록. {@link #full()} 뒤에는 등록을 거부한다. */
public class RecordingRealtimeConnectionPort implements RealtimeConnectionPort {

    private final List<Long> registeredAccounts = new ArrayList<>();
    private final List<String> sent = new ArrayList<>();
    private boolean full;

    @Override
    public boolean register(Long accountId, Long organizationId, ClientType clientType, RealtimeChannel channel) {
        if (full) {
            return false;
        }
        registeredAccounts.add(accountId);
        return true;
    }

    @Override
    public void sendToOrganization(Long organizationId, RealtimeMessage message) {
        sent.add("organization " + organizationId + " " + message);
    }

    @Override
    public void sendToAccount(Long accountId, RealtimeMessage message) {
        sent.add("account " + accountId + " " + message);
    }

    public void full() {
        this.full = true;
    }

    public List<Long> registeredAccounts() {
        return List.copyOf(registeredAccounts);
    }

    /** "organization {id} {이벤트}" 또는 "account {id} {이벤트}" 형식으로 보낸 순서대로 돌려준다. */
    public List<String> sent() {
        return List.copyOf(sent);
    }
}
