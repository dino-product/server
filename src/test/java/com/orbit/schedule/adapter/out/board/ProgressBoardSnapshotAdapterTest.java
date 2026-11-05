package com.orbit.schedule.adapter.out.board;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.util.unit.DataSize;

import com.orbit.schedule.application.port.in.query.dto.LabelInfo;
import com.orbit.schedule.application.port.in.query.dto.TechnicianInfo;
import com.orbit.schedule.application.port.out.ProgressBoardBusyException;
import com.orbit.schedule.application.port.out.ProgressBoardSnapshot;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.WorkStatus;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("진행 보드 스냅샷 어댑터")
class ProgressBoardSnapshotAdapterTest {

    private static final OrganizationId ORGANIZATION = new OrganizationId(7L);
    private static final Instant FROM = Instant.parse("2030-03-02T00:00:00Z");
    private static final Instant TO = FROM.plus(Duration.ofDays(1));
    private static final ProgressBoardSnapshot BOARD = new ProgressBoardSnapshot(List.of(new ProgressBoardSnapshot.Card(
            11L,
            WorkStatus.IN_PROGRESS,
            "에어컨 설치",
            new TechnicianInfo(3L, "김민수", null),
            FROM,
            FROM.plus(Duration.ofHours(2)),
            "홍길동",
            false,
            FROM.plus(Duration.ofHours(2)))));

    private final FakeStore store = new FakeStore();
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final MutableClock clock = new MutableClock(Instant.parse("2030-03-02T09:00:00Z"));
    private final List<CompletableFuture<?>> background = new ArrayList<>();

    @AfterEach
    void finishBackground() {
        background.forEach(future -> future.cancel(true));
    }

    @Test
    @DisplayName("저장한 보드가 없으면 만들어 저장하고, 다음 조회는 만들지 않고 저장한 보드를 돌려준다")
    void storesBuiltBoardAndServesItNextTime() {
        ProgressBoardSnapshotAdapter adapter = adapter(2, Duration.ofSeconds(1));
        AtomicInteger builds = new AtomicInteger();

        ProgressBoardSnapshot first = adapter.load(ORGANIZATION, FROM, TO, counting(builds));
        ProgressBoardSnapshot second = adapter.load(ORGANIZATION, FROM, TO, counting(builds));

        assertThat(first).isEqualTo(BOARD);
        assertThat(second).isEqualTo(BOARD);
        assertThat(builds).hasValue(1);
        assertThat(count("schedule.board.cache", "miss")).isEqualTo(1);
        assertThat(count("schedule.board.cache", "hit")).isEqualTo(1);
        assertThat(count("schedule.board.cache.store", "stored")).isEqualTo(1);
        assertThat(meters.get("schedule.board.build").timer().count()).isEqualTo(1);
    }

    @Test
    @DisplayName("무효화하면 다음 조회는 새로 만든다")
    void buildsAgainAfterInvalidation() {
        ProgressBoardSnapshotAdapter adapter = adapter(2, Duration.ofSeconds(1));
        AtomicInteger builds = new AtomicInteger();
        adapter.load(ORGANIZATION, FROM, TO, counting(builds));

        adapter.invalidate(ORGANIZATION);
        adapter.load(ORGANIZATION, FROM, TO, counting(builds));

        assertThat(builds).hasValue(2);
        assertThat(count("schedule.board.cache.invalidate", "success")).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 보드를 만드는 동안 온 조회는 새로 만들지 않고 그 결과를 함께 받는다")
    void joinsRunningBuildOfSameGeneration() throws Exception {
        ProgressBoardSnapshotAdapter adapter = adapter(2, Duration.ofSeconds(5));
        CountDownLatch building = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger builds = new AtomicInteger();
        CompletableFuture<ProgressBoardSnapshot> leader = async(() -> adapter.load(ORGANIZATION, FROM, TO, () -> {
            builds.incrementAndGet();
            building.countDown();
            await(release);
            return BOARD;
        }));
        assertThat(building.await(5, TimeUnit.SECONDS)).isTrue();

        CompletableFuture<ProgressBoardSnapshot> follower =
                async(() -> adapter.load(ORGANIZATION, FROM, TO, counting(builds)));
        awaitCount("schedule.board.cache", "joined", 1);
        release.countDown();

        assertThat(leader.get(5, TimeUnit.SECONDS)).isEqualTo(BOARD);
        assertThat(follower.get(5, TimeUnit.SECONDS)).isEqualTo(BOARD);
        assertThat(builds).hasValue(1);
    }

    @Test
    @DisplayName("무효화 뒤에 온 조회는 무효화 전에 시작한 만들기에 합류하지 않고 새로 만들며, 무효화 전 데이터로 만든 보드는 저장하지 않는다")
    void doesNotJoinOrStoreBuildStartedBeforeInvalidation() throws Exception {
        ProgressBoardSnapshotAdapter adapter = adapter(2, Duration.ofSeconds(5));
        CountDownLatch building = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger builds = new AtomicInteger();
        CompletableFuture<ProgressBoardSnapshot> stale = async(() -> adapter.load(ORGANIZATION, FROM, TO, () -> {
            builds.incrementAndGet();
            building.countDown();
            await(release);
            return BOARD;
        }));
        assertThat(building.await(5, TimeUnit.SECONDS)).isTrue();

        adapter.invalidate(ORGANIZATION);
        ProgressBoardSnapshot fresh = adapter.load(ORGANIZATION, FROM, TO, counting(builds));
        release.countDown();
        stale.get(5, TimeUnit.SECONDS);

        assertThat(fresh).isEqualTo(BOARD);
        assertThat(builds).hasValue(2);
        assertThat(count("schedule.board.cache", "joined")).isZero();
        assertThat(count("schedule.board.cache.store", "generation_changed")).isEqualTo(1);
        assertThat(count("schedule.board.cache.store", "stored")).isEqualTo(1);
    }

    @Test
    @DisplayName("동시에 만드는 수가 상한이면 기다리지 않고 거절한다")
    void rejectsBuildBeyondLimit() throws Exception {
        ProgressBoardSnapshotAdapter adapter = adapter(1, Duration.ofSeconds(5));
        CountDownLatch building = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<ProgressBoardSnapshot> running = async(() -> adapter.load(ORGANIZATION, FROM, TO, () -> {
            building.countDown();
            await(release);
            return BOARD;
        }));
        assertThat(building.await(5, TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(() -> adapter.load(new OrganizationId(8L), FROM, TO, () -> BOARD))
                .isInstanceOf(ProgressBoardBusyException.class);
        release.countDown();
        assertThat(running.get(5, TimeUnit.SECONDS)).isEqualTo(BOARD);
    }

    @Test
    @DisplayName("같은 보드를 기다리다 한도를 넘으면 거절한다")
    void rejectsWhenJoinTimesOut() throws Exception {
        ProgressBoardSnapshotAdapter adapter = adapter(2, Duration.ofMillis(100));
        CountDownLatch building = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<ProgressBoardSnapshot> running = async(() -> adapter.load(ORGANIZATION, FROM, TO, () -> {
            building.countDown();
            await(release);
            return BOARD;
        }));
        assertThat(building.await(5, TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(() -> adapter.load(ORGANIZATION, FROM, TO, () -> BOARD))
                .isInstanceOf(ProgressBoardBusyException.class);
        assertThat(count("schedule.board.cache", "join_timeout")).isEqualTo(1);
        release.countDown();
        running.get(5, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("만들기가 실패하면 기다리던 조회도 같은 예외를 받고, 다음 조회는 다시 만든다")
    void propagatesBuildFailureToJoinedRequests() throws Exception {
        ProgressBoardSnapshotAdapter adapter = adapter(2, Duration.ofSeconds(5));
        CountDownLatch building = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<ProgressBoardSnapshot> leader = async(() -> adapter.load(ORGANIZATION, FROM, TO, () -> {
            building.countDown();
            await(release);
            throw new IllegalStateException("db down");
        }));
        assertThat(building.await(5, TimeUnit.SECONDS)).isTrue();
        CompletableFuture<ProgressBoardSnapshot> follower =
                async(() -> adapter.load(ORGANIZATION, FROM, TO, () -> BOARD));
        awaitCount("schedule.board.cache", "joined", 1);
        release.countDown();

        assertThat(follower)
                .failsWithin(Duration.ofSeconds(5))
                .withThrowableOfType(java.util.concurrent.ExecutionException.class)
                .withCauseInstanceOf(IllegalStateException.class);
        assertThat(leader).failsWithin(Duration.ofSeconds(5));
        assertThat(adapter.load(ORGANIZATION, FROM, TO, () -> BOARD)).isEqualTo(BOARD);
    }

    @Test
    @DisplayName("캐시를 끄면 조회마다 만들고 묶지 않는다")
    void buildsEveryTimeWhenCacheDisabled() {
        ProgressBoardSnapshotAdapter adapter = adapter(null, 2, Duration.ofSeconds(1));
        AtomicInteger builds = new AtomicInteger();

        adapter.load(ORGANIZATION, FROM, TO, counting(builds));
        adapter.load(ORGANIZATION, FROM, TO, counting(builds));
        adapter.invalidate(ORGANIZATION);

        assertThat(builds).hasValue(2);
        assertThat(count("schedule.board.cache", "bypass_disabled")).isEqualTo(2);
    }

    @Test
    @DisplayName("캐시 Redis가 실패하면 DB로 만들고, 잠시 동안은 캐시를 건너뛴 뒤 다시 쓴다")
    void bypassesCacheForAWhileAfterFailure() {
        ProgressBoardSnapshotAdapter adapter = adapter(2, Duration.ofSeconds(1));
        AtomicInteger builds = new AtomicInteger();
        store.failing = true;

        assertThat(adapter.load(ORGANIZATION, FROM, TO, counting(builds))).isEqualTo(BOARD);
        store.failing = false;
        adapter.load(ORGANIZATION, FROM, TO, counting(builds));
        assertThat(store.finds).as("건너뛰는 동안은 캐시를 읽지 않는다").hasValue(1);

        clock.advance(Duration.ofSeconds(6));
        adapter.load(ORGANIZATION, FROM, TO, counting(builds));
        adapter.load(ORGANIZATION, FROM, TO, counting(builds));

        assertThat(builds).hasValue(3);
        assertThat(count("schedule.board.cache", "bypass_unavailable")).isEqualTo(2);
        assertThat(count("schedule.board.cache", "hit")).isEqualTo(1);
    }

    @Test
    @DisplayName("무효화가 실패하면 한 번 다시 시도하고, 또 실패하면 던지지 않고 센다")
    void retriesInvalidationOnce() {
        ProgressBoardSnapshotAdapter adapter = adapter(2, Duration.ofSeconds(1));
        store.invalidateFailures = 1;
        adapter.invalidate(ORGANIZATION);
        assertThat(count("schedule.board.cache.invalidate", "retried")).isEqualTo(1);

        store.invalidateFailures = 2;
        adapter.invalidate(ORGANIZATION);
        assertThat(count("schedule.board.cache.invalidate", "failure")).isEqualTo(1);
        assertThat(store.generations.get(ORGANIZATION)).isEqualTo(1L);
    }

    @Test
    @DisplayName("캐시 Redis가 실패한 직후의 무효화는 다시 시도하지 않고 한 번만 시도하며, 무효화 실패도 잠시 캐시를 건너뛰게 한다")
    void invalidatesOnceWhileUnavailable() {
        ProgressBoardSnapshotAdapter adapter = adapter(2, Duration.ofSeconds(1));
        store.invalidateFailures = 2;
        adapter.invalidate(ORGANIZATION);
        assertThat(store.invalidateAttempts).hasValue(2);

        store.invalidateFailures = 1;
        adapter.invalidate(ORGANIZATION);

        assertThat(store.invalidateAttempts).as("건너뛰는 동안은 한 번만").hasValue(3);
        assertThat(count("schedule.board.cache.invalidate", "failure")).isEqualTo(2);
        adapter.load(ORGANIZATION, FROM, TO, () -> BOARD);
        assertThat(count("schedule.board.cache", "bypass_unavailable")).isEqualTo(1);
    }

    @Test
    @DisplayName("저장값 모양은 정해진 JSON이다(바꾸면 RedisBoardCacheStore 키의 형식 버전을 올린다)")
    void keepsStoredJsonShape() {
        ProgressBoardSnapshot snapshot = new ProgressBoardSnapshot(List.of(new ProgressBoardSnapshot.Card(
                11L,
                WorkStatus.COMPLETED,
                "에어컨 설치",
                new TechnicianInfo(3L, "김민수", new LabelInfo(7L, "일반 기사", "#3366FF")),
                FROM,
                null,
                null,
                true,
                Instant.EPOCH)));
        JsonMapper json = JsonMapper.builder().build();

        String value = json.writeValueAsString(snapshot);

        assertThat(value)
                .isEqualTo("{\"cards\":[{\"workId\":11,\"status\":\"COMPLETED\",\"name\":\"에어컨 설치\","
                        + "\"technician\":{\"technicianId\":3,\"name\":\"김민수\",\"technicianType\":"
                        + "{\"id\":7,\"name\":\"일반 기사\",\"color\":\"#3366FF\"}},"
                        + "\"startTime\":\"2030-03-02T00:00:00Z\",\"endTime\":null,\"customerName\":null,"
                        + "\"returnedByRejection\":true,\"delayThreshold\":\"1970-01-01T00:00:00Z\"}]}");
        assertThat(json.readValue(value, ProgressBoardSnapshot.class)).isEqualTo(snapshot);
    }

    @Test
    @DisplayName("풀 수 없는 저장값은 없는 것으로 보고 새로 만든다")
    void treatsUndecodableValueAsMiss() {
        ProgressBoardSnapshotAdapter adapter = adapter(2, Duration.ofSeconds(1));
        store.values.put(ORGANIZATION.value() + "|" + FROM + "/" + TO, "{\"cards\":[{\"workId\":\"x\"}]");
        AtomicInteger builds = new AtomicInteger();

        assertThat(adapter.load(ORGANIZATION, FROM, TO, counting(builds))).isEqualTo(BOARD);

        assertThat(builds).hasValue(1);
        assertThat(count("schedule.board.cache", "decode_failed")).isEqualTo(1);
    }

    @Test
    @DisplayName("크기 상한을 넘는 보드는 저장하지 않는다")
    void skipsStoringTooLargeBoard() {
        ProgressBoardSnapshotAdapter adapter = adapter(store, 2, Duration.ofSeconds(1), DataSize.ofBytes(10));

        adapter.load(ORGANIZATION, FROM, TO, () -> BOARD);

        assertThat(store.values).isEmpty();
        assertThat(count("schedule.board.cache.store", "too_large")).isEqualTo(1);
    }

    private ProgressBoardSnapshotAdapter adapter(int maxBuilds, Duration joinTimeout) {
        return adapter(store, maxBuilds, joinTimeout);
    }

    private ProgressBoardSnapshotAdapter adapter(BoardCacheStore cacheStore, int maxBuilds, Duration joinTimeout) {
        return adapter(cacheStore, maxBuilds, joinTimeout, DataSize.ofKilobytes(256));
    }

    private ProgressBoardSnapshotAdapter adapter(
            BoardCacheStore cacheStore, int maxBuilds, Duration joinTimeout, DataSize maxValueSize) {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        if (cacheStore != null) {
            beans.addBean("store", cacheStore);
        }
        ObjectProvider<BoardCacheStore> provider = beans.getBeanProvider(BoardCacheStore.class);
        return new ProgressBoardSnapshotAdapter(
                provider,
                new ProgressBoardBuildProperties(maxBuilds, joinTimeout),
                new BoardCacheProperties(true, null, null, null, null, null, Duration.ofSeconds(5), null, maxValueSize),
                JsonMapper.builder().build(),
                meters,
                clock);
    }

    private static Supplier<ProgressBoardSnapshot> counting(AtomicInteger builds) {
        return () -> {
            builds.incrementAndGet();
            return BOARD;
        };
    }

    private <T> CompletableFuture<T> async(Supplier<T> task) {
        CompletableFuture<T> future = CompletableFuture.supplyAsync(task);
        background.add(future);
        return future;
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("latch timed out");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private void awaitCount(String name, String result, double expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (count(name, result) < expected) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError(name + " " + result + " did not reach " + expected);
            }
            Thread.sleep(5);
        }
    }

    private double count(String name, String result) {
        var counter = meters.find(name).tag("result", result).counter();
        return counter == null ? 0 : counter.count();
    }

    /** 메모리 저장소. 세대 비교·무효화를 Redis 스크립트와 같은 규칙으로 흉내 낸다. */
    private static final class FakeStore implements BoardCacheStore {

        private final Map<String, String> values = new HashMap<>();
        private final Map<OrganizationId, Long> generations = new HashMap<>();
        private final AtomicInteger finds = new AtomicInteger();
        private final AtomicInteger invalidateAttempts = new AtomicInteger();
        private volatile boolean failing;
        private int invalidateFailures;

        @Override
        public synchronized Optional<String> find(OrganizationId organizationId, String period) {
            finds.incrementAndGet();
            failIfFailing();
            return Optional.ofNullable(values.get(organizationId.value() + "|" + period));
        }

        @Override
        public synchronized long generation(OrganizationId organizationId) {
            failIfFailing();
            return generations.getOrDefault(organizationId, 0L);
        }

        @Override
        public synchronized StoreResult store(
                OrganizationId organizationId, String period, long generation, String value) {
            failIfFailing();
            if (generations.getOrDefault(organizationId, 0L) != generation) {
                return StoreResult.GENERATION_CHANGED;
            }
            values.put(organizationId.value() + "|" + period, value);
            return StoreResult.STORED;
        }

        @Override
        public synchronized void invalidate(OrganizationId organizationId) {
            invalidateAttempts.incrementAndGet();
            if (invalidateFailures > 0) {
                invalidateFailures--;
                throw new IllegalStateException("redis down");
            }
            generations.merge(organizationId, 1L, Long::sum);
            values.keySet().removeIf(key -> key.startsWith(organizationId.value() + "|"));
        }

        private void failIfFailing() {
            if (failing) {
                throw new IllegalStateException("redis down");
            }
        }
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
