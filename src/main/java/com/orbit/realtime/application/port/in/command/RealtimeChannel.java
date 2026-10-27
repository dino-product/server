package com.orbit.realtime.application.port.in.command;

import java.io.IOException;

import com.orbit.realtime.domain.RealtimeMessage;

/**
 * 요청한 화면으로 이어진 연결 하나. 웹 어댑터가 실제 전송 수단으로 구현해 연결 열기 요청에 넘긴다. 보내기는 한 번에 한 스레드만 부르고, 닫기는 어느 스레드에서나
 * 부를 수 있다.
 */
public interface RealtimeChannel {

    /** 이벤트를 보낸다. 보내지 못하면(화면이 연결을 끊음, 이미 닫힘) 예외를 던진다. 화면이 받지 않으면 전송 수단의 쓰기 시간 한도까지 막힐 수 있다. */
    void send(RealtimeMessage message) throws IOException;

    /** 연결이 살아 있게 하는, 화면이 무시하는 신호를 보낸다. 실패는 {@link #send}와 같다. */
    void sendHeartbeat() throws IOException;

    /** 연결을 닫는다. 이미 닫혔으면 아무것도 하지 않는다. 보내기가 막혀 있으면 그 쓰기가 끝날 때까지 기다릴 수 있다. */
    void close();

    /** 연결이 어떤 이유로든 닫히면 한 번 부를 동작을 건다. 이미 닫혔으면 바로 부른다. */
    void onClosed(Runnable callback);
}
