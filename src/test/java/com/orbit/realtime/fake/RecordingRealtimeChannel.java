package com.orbit.realtime.fake;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.orbit.realtime.application.port.in.command.RealtimeChannel;
import com.orbit.realtime.domain.RealtimeMessage;

/** 보낸 이벤트·유지 신호·닫힘을 기록하는 연결. 쓰기 실패와 막힌 쓰기를 흉내 낼 수 있다. */
public class RecordingRealtimeChannel implements RealtimeChannel {

    private final List<RealtimeMessage> sent = new CopyOnWriteArrayList<>();
    private final AtomicInteger heartbeats = new AtomicInteger();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicReference<Runnable> onClosed = new AtomicReference<>();
    private volatile boolean failWrites;
    private volatile CountDownLatch blockWrites;
    private volatile boolean failAfterBlock;
    private final CountDownLatch writeStarted = new CountDownLatch(1);

    @Override
    public void send(RealtimeMessage message) throws IOException {
        write();
        sent.add(message);
    }

    @Override
    public void sendHeartbeat() throws IOException {
        write();
        heartbeats.incrementAndGet();
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            runOnClosed();
        }
    }

    @Override
    public void onClosed(Runnable callback) {
        onClosed.set(callback);
        if (closed.get()) {
            runOnClosed();
        }
    }

    /** 화면이 연결을 끊었다. */
    public void disconnect() {
        close();
    }

    /** 이후 쓰기는 모두 실패한다. */
    public void failWrites() {
        this.failWrites = true;
    }

    /** 다음 쓰기 하나는 {@code release}가 열릴 때까지 막혔다가, {@code fail}이면 실패하고 아니면 보낸다. */
    public void blockNextWrite(CountDownLatch release, boolean fail) {
        this.failAfterBlock = fail;
        this.blockWrites = release;
    }

    /** 막힌 쓰기가 시작될 때까지 기다린다. */
    public void awaitWriteStarted() throws InterruptedException {
        if (!writeStarted.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("write did not start");
        }
    }

    public List<RealtimeMessage> sent() {
        return List.copyOf(sent);
    }

    public int heartbeats() {
        return heartbeats.get();
    }

    public boolean isClosed() {
        return closed.get();
    }

    private void write() throws IOException {
        if (closed.get()) {
            throw new IllegalStateException("channel already closed");
        }
        CountDownLatch release = blockWrites;
        if (release != null) {
            blockWrites = null;
            writeStarted.countDown();
            try {
                release.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            if (failAfterBlock) {
                throw new IOException("write timed out");
            }
        }
        if (failWrites) {
            throw new IOException("broken pipe");
        }
    }

    private void runOnClosed() {
        Runnable callback = onClosed.getAndSet(null);
        if (callback != null) {
            callback.run();
        }
    }
}
