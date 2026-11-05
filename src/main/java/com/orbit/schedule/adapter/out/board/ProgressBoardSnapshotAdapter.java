package com.orbit.schedule.adapter.out.board;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.orbit.schedule.application.port.out.ProgressBoardBusyException;
import com.orbit.schedule.application.port.out.ProgressBoardSnapshot;
import com.orbit.schedule.application.port.out.ProgressBoardSnapshotPort;
import com.orbit.schedule.domain.OrganizationId;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * 진행 보드 스냅샷을 캐시 Redis({@link BoardCacheStore})에 두고 나눠 쓴다. 캐시를 끄거나 캐시 Redis에 닿지 않으면 조회마다 DB에서 만든다.
 *
 * <ul>
 *   <li>DB에서 만드는 수는 인스턴스마다 {@link ProgressBoardBuildProperties#maxConcurrentBuilds} 이하이고, 넘으면 기다리지 않고 거절한다.
 *   <li>캐시에 없으면 세대를 먼저 읽고 (조직, 구간, 세대)마다 한 요청만 만든다. 같은 키의 다른 요청은 그 결과를 {@link
 *       ProgressBoardBuildProperties#joinTimeout}까지 기다린다. 세대가 키에 있어서, 무효화 뒤에 온 요청은 무효화 전에 시작한 만들기(옛 데이터일 수 있음)에
 *       합류하지 않는다. 세대를 읽지 못하면 묶지 않는다.
 *   <li>만든 보드는 만들기 전에 읽은 세대가 그대로일 때만 저장한다. 크기·구간 수 상한을 넘으면 저장하지 않는다.
 *   <li>캐시 Redis 명령이 실패하면 {@link BoardCacheProperties#unavailableBackoff} 동안 조회·저장을 건너뛴다. 무효화는 건너뛰지 않는다. 실패하면 한 번
 *       다시 시도하되 건너뛰는 동안에는 한 번만 시도하고, 그래도 실패하면 로그만 남긴다(옛 값은 TTL 안에 사라진다).
 *   <li>읽은 값을 풀지 못하면(형식이 다른 배포 등) 없는 것으로 본다.
 * </ul>
 *
 * <p>지표: {@code schedule.board.cache}(result), {@code schedule.board.cache.store}(result), {@code
 * schedule.board.cache.invalidate}(result), {@code schedule.board.build}(DB에서 만든 시간).
 */
@Slf4j
@Component
class ProgressBoardSnapshotAdapter implements ProgressBoardSnapshotPort {

    private final BoardCacheStore store;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meters;
    private final Clock clock;
    private final Semaphore builds;
    private final long joinTimeoutMillis;
    private final long maxValueBytes;
    private final long unavailableBackoffMillis;
    private final Timer buildTimer;
    private final ConcurrentMap<FlightKey, CompletableFuture<ProgressBoardSnapshot>> flights =
            new ConcurrentHashMap<>();
    private volatile long unavailableUntilMillis;

    ProgressBoardSnapshotAdapter(
            ObjectProvider<BoardCacheStore> store,
            ProgressBoardBuildProperties buildProperties,
            BoardCacheProperties cacheProperties,
            ObjectMapper objectMapper,
            MeterRegistry meters,
            Clock clock) {
        this.store = store.getIfAvailable();
        this.objectMapper = objectMapper;
        this.meters = meters;
        this.clock = clock;
        this.builds = new Semaphore(buildProperties.maxConcurrentBuilds());
        this.joinTimeoutMillis = buildProperties.joinTimeout().toMillis();
        this.maxValueBytes = cacheProperties.maxValueSize().toBytes();
        this.unavailableBackoffMillis = cacheProperties.unavailableBackoff().toMillis();
        this.buildTimer = Timer.builder("schedule.board.build").register(meters);
    }

    @Override
    public ProgressBoardSnapshot load(
            OrganizationId organizationId, Instant from, Instant to, Supplier<ProgressBoardSnapshot> builder) {
        if (store == null) {
            count("bypass_disabled");
            return build(builder);
        }
        if (unavailable()) {
            count("bypass_unavailable");
            return build(builder);
        }
        String period = from + "/" + to;
        long generation;
        try {
            Optional<ProgressBoardSnapshot> cached =
                    store.find(organizationId, period).flatMap(this::decode);
            if (cached.isPresent()) {
                count("hit");
                return cached.get();
            }
            generation = store.generation(organizationId);
        } catch (RuntimeException exception) {
            unavailable(exception);
            count("bypass_unavailable");
            return build(builder);
        }
        return buildOnce(new FlightKey(organizationId, period, generation), builder);
    }

    @Override
    public void invalidate(OrganizationId organizationId) {
        if (store == null) {
            return;
        }
        // 요청 스레드가 커밋 뒤 DB 커넥션을 쥔 채 부르므로, 캐시 Redis가 실패한 직후에는 다시 시도하지 않고 한 번만 시도한다.
        int attempts = unavailable() ? 1 : 2;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                store.invalidate(organizationId);
                meters.counter("schedule.board.cache.invalidate", "result", attempt == 1 ? "success" : "retried")
                        .increment();
                return;
            } catch (RuntimeException exception) {
                if (attempt == attempts) {
                    markUnavailable();
                    log.warn(
                            "Progress board cache invalidation failed: organizationId={}",
                            organizationId.value(),
                            exception);
                }
            }
        }
        meters.counter("schedule.board.cache.invalidate", "result", "failure").increment();
    }

    private ProgressBoardSnapshot buildOnce(FlightKey key, Supplier<ProgressBoardSnapshot> builder) {
        CompletableFuture<ProgressBoardSnapshot> mine = new CompletableFuture<>();
        CompletableFuture<ProgressBoardSnapshot> running = flights.putIfAbsent(key, mine);
        if (running != null) {
            count("joined");
            return join(running);
        }
        count("miss");
        try {
            ProgressBoardSnapshot snapshot = build(builder);
            save(key, snapshot);
            mine.complete(snapshot);
            return snapshot;
        } catch (RuntimeException | Error failure) {
            mine.completeExceptionally(failure);
            throw failure;
        } finally {
            flights.remove(key, mine);
        }
    }

    private ProgressBoardSnapshot join(CompletableFuture<ProgressBoardSnapshot> running) {
        try {
            return running.get(joinTimeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            count("join_timeout");
            throw new ProgressBoardBusyException("progress board build did not finish within the join timeout");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ProgressBoardBusyException("interrupted while waiting for a progress board build");
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            if (exception.getCause() instanceof Error cause) {
                throw cause;
            }
            throw new IllegalStateException("progress board build failed", exception.getCause());
        }
    }

    private ProgressBoardSnapshot build(Supplier<ProgressBoardSnapshot> builder) {
        if (!builds.tryAcquire()) {
            throw new ProgressBoardBusyException("too many progress board builds");
        }
        try {
            return buildTimer.record(builder);
        } finally {
            builds.release();
        }
    }

    /** 저장하지 못해도 응답은 그대로 한다. */
    private void save(FlightKey key, ProgressBoardSnapshot snapshot) {
        String value;
        try {
            value = objectMapper.writeValueAsString(snapshot);
        } catch (JacksonException exception) {
            log.warn("Progress board snapshot could not be encoded", exception);
            countStore("failure");
            return;
        }
        if (value.getBytes(StandardCharsets.UTF_8).length > maxValueBytes) {
            countStore("too_large");
            return;
        }
        try {
            countStore(
                    switch (store.store(key.organizationId(), key.period(), key.generation(), value)) {
                        case STORED -> "stored";
                        case GENERATION_CHANGED -> "generation_changed";
                        case TOO_MANY_PERIODS -> "too_many_periods";
                    });
        } catch (RuntimeException exception) {
            unavailable(exception);
            countStore("failure");
        }
    }

    private Optional<ProgressBoardSnapshot> decode(String value) {
        try {
            return Optional.of(objectMapper.readValue(value, ProgressBoardSnapshot.class));
        } catch (JacksonException exception) {
            log.debug("Progress board cache value could not be decoded", exception);
            count("decode_failed");
            return Optional.empty();
        }
    }

    private boolean unavailable() {
        return clock.instant().toEpochMilli() < unavailableUntilMillis;
    }

    private void unavailable(RuntimeException exception) {
        markUnavailable();
        log.warn("Progress board cache is unavailable; building from the database for a while", exception);
    }

    private void markUnavailable() {
        unavailableUntilMillis = clock.instant().toEpochMilli() + unavailableBackoffMillis;
    }

    private void countStore(String result) {
        meters.counter("schedule.board.cache.store", "result", result).increment();
    }

    private void count(String result) {
        meters.counter("schedule.board.cache", "result", result).increment();
    }

    private record FlightKey(OrganizationId organizationId, String period, long generation) {}
}
