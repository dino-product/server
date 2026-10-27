package com.orbit.realtime.adapter.in.web;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.orbit.realtime.application.port.in.command.RealtimeChannel;
import com.orbit.realtime.domain.RealtimeMessage;
import com.orbit.realtime.domain.RealtimeMessage.Closed;
import com.orbit.realtime.domain.RealtimeMessage.Connected;
import com.orbit.realtime.domain.RealtimeMessage.Reconnect;

/**
 * SSE로 구현한 실시간 연결. 이벤트 이름은 {@link RealtimeMessage#name()}을 쓰고 이벤트 값(JSON)은 여기서 정한다. 연결 유지 신호는 화면이 무시하는 주석
 * 줄({@code :ping})이다. 안전 한도가 지나면 오류로 다시 디스패치하지 않도록 바로 완료한다.
 */
final class SseRealtimeChannel implements RealtimeChannel {

    private final SseEmitter emitter;
    private final AtomicBoolean closeRequested = new AtomicBoolean();
    private final AtomicBoolean finished = new AtomicBoolean();
    private final AtomicReference<Runnable> onClosed = new AtomicReference<>();

    SseRealtimeChannel(Duration timeout) {
        this.emitter = new SseEmitter(timeout.toMillis());
        emitter.onTimeout(this::close);
        emitter.onCompletion(this::finish);
        emitter.onError(error -> finish());
    }

    SseEmitter emitter() {
        return emitter;
    }

    @Override
    public void send(RealtimeMessage message) throws IOException {
        emitter.send(SseEmitter.event().name(message.name()).data(payload(message), MediaType.APPLICATION_JSON));
    }

    @Override
    public void sendHeartbeat() throws IOException {
        emitter.send(SseEmitter.event().comment("ping"));
    }

    @Override
    public void close() {
        if (closeRequested.compareAndSet(false, true)) {
            emitter.complete();
        }
    }

    @Override
    public void onClosed(Runnable callback) {
        onClosed.set(callback);
        if (finished.get()) {
            runOnClosed();
        }
    }

    private void finish() {
        finished.set(true);
        runOnClosed();
    }

    private void runOnClosed() {
        Runnable callback = onClosed.getAndSet(null);
        if (callback != null) {
            callback.run();
        }
    }

    private static Map<String, Object> payload(RealtimeMessage message) {
        return switch (message) {
            case Connected connected -> Map.of();
            case Reconnect reconnect -> Map.of();
            case Closed closed -> Map.of("reason", closed.reason());
        };
    }
}
