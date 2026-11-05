package com.orbit.schedule.adapter.out.board;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orbit.schedule.adapter.out.board.BoardCacheStore.StoreResult;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.support.RedisAdapterTestSupport;
import com.orbit.support.SharedContainers;

@DisplayName("진행 보드 캐시 Redis 저장소")
class RedisBoardCacheStoreTest extends RedisAdapterTestSupport {

    private static final OrganizationId ORGANIZATION = new OrganizationId(7L);
    private static final OrganizationId OTHER = new OrganizationId(8L);

    private RedisBoardCacheStore store;

    @BeforeEach
    void connect() {
        store = new RedisBoardCacheStore(
                SharedContainers.REDIS.getHost(),
                SharedContainers.REDIS.getMappedPort(SharedContainers.REDIS_PORT),
                Duration.ofSeconds(1),
                Duration.ofSeconds(60),
                2,
                null);
        redisTemplate.delete(RedisBoardCacheStore.boardKey(ORGANIZATION));
        redisTemplate.delete(RedisBoardCacheStore.generationKey(ORGANIZATION));
        redisTemplate.delete(RedisBoardCacheStore.boardKey(OTHER));
        redisTemplate.delete(RedisBoardCacheStore.generationKey(OTHER));
    }

    @AfterEach
    void close() {
        store.close();
    }

    @Test
    @DisplayName("세대가 그대로면 구간별로 저장하고, TTL은 처음 저장할 때만 걸어 다른 구간을 저장해도 늘리지 않는다")
    void storesPerPeriodWithTtlFromFirstStore() {
        long generation = store.generation(ORGANIZATION);

        assertThat(store.store(ORGANIZATION, "a", generation, "board-a")).isEqualTo(StoreResult.STORED);
        redisTemplate.expire(RedisBoardCacheStore.boardKey(ORGANIZATION), Duration.ofSeconds(5));
        assertThat(store.store(ORGANIZATION, "b", generation, "board-b")).isEqualTo(StoreResult.STORED);

        assertThat(store.find(ORGANIZATION, "a")).contains("board-a");
        assertThat(store.find(ORGANIZATION, "b")).contains("board-b");
        assertThat(store.find(ORGANIZATION, "c")).isEmpty();
        assertThat(redisTemplate.getExpire(RedisBoardCacheStore.boardKey(ORGANIZATION)))
                .isBetween(1L, 5L);
    }

    @Test
    @DisplayName("세대 키가 없으면 읽을 때 서버 시각으로 심고, 세대 키가 없어진 뒤에는 앞서 읽은 세대로 저장하지 않는다")
    void seedsGenerationAndRejectsStoreWithoutIt() {
        long generation = store.generation(ORGANIZATION);
        assertThat(store.generation(ORGANIZATION)).as("한 번 심으면 그대로").isEqualTo(generation);
        assertThat(generation).isGreaterThan(1_000_000_000_000_000L);

        redisTemplate.delete(RedisBoardCacheStore.generationKey(ORGANIZATION));

        assertThat(store.store(ORGANIZATION, "a", generation, "stale")).isEqualTo(StoreResult.GENERATION_CHANGED);
        assertThat(store.store(ORGANIZATION, "a", 0, "stale")).isEqualTo(StoreResult.GENERATION_CHANGED);
        assertThat(store.find(ORGANIZATION, "a")).isEmpty();
    }

    @Test
    @DisplayName("무효화는 세대를 바꾸고 그 조직의 값만 지우며, 무효화 전에 읽은 세대로는 저장하지 않는다")
    void invalidationChangesGenerationAndRejectsStaleStore() {
        long before = store.generation(ORGANIZATION);
        store.store(ORGANIZATION, "a", before, "old");
        store.store(OTHER, "a", store.generation(OTHER), "other");

        store.invalidate(ORGANIZATION);

        long after = store.generation(ORGANIZATION);
        assertThat(after).isNotEqualTo(before);
        assertThat(store.find(ORGANIZATION, "a")).isEmpty();
        assertThat(store.find(OTHER, "a")).contains("other");
        assertThat(store.store(ORGANIZATION, "a", before, "stale")).isEqualTo(StoreResult.GENERATION_CHANGED);
        assertThat(store.find(ORGANIZATION, "a")).isEmpty();
        assertThat(store.store(ORGANIZATION, "a", after, "fresh")).isEqualTo(StoreResult.STORED);
        assertThat(redisTemplate.getExpire(RedisBoardCacheStore.generationKey(ORGANIZATION)))
                .isGreaterThan(Duration.ofDays(6).toSeconds());
    }

    @Test
    @DisplayName("세대 키가 사라진 뒤 다시 만들면 앞서 읽은 세대와 겹치지 않는 값에서 시작한다")
    void restartsGenerationAboveEarlierValues() {
        store.invalidate(ORGANIZATION);
        long first = store.generation(ORGANIZATION);
        redisTemplate.delete(RedisBoardCacheStore.generationKey(ORGANIZATION));

        store.invalidate(ORGANIZATION);

        assertThat(first).isGreaterThan(1_000_000_000_000_000L);
        assertThat(store.generation(ORGANIZATION)).isNotEqualTo(first).isNotZero();
    }

    @Test
    @DisplayName("조직의 구간 수가 상한이면 새 구간은 저장하지 않고, 이미 있는 구간은 바꾼다")
    void limitsPeriodsPerOrganization() {
        long generation = store.generation(ORGANIZATION);
        store.store(ORGANIZATION, "a", generation, "a1");
        store.store(ORGANIZATION, "b", generation, "b1");

        assertThat(store.store(ORGANIZATION, "c", generation, "c1")).isEqualTo(StoreResult.TOO_MANY_PERIODS);
        assertThat(store.store(ORGANIZATION, "a", generation, "a2")).isEqualTo(StoreResult.STORED);

        assertThat(store.find(ORGANIZATION, "c")).isEmpty();
        assertThat(store.find(ORGANIZATION, "a")).contains("a2");
    }

    @Test
    @DisplayName("닿지 않는 Redis에는 짧은 한도 안에 실패한다")
    void failsFastWhenUnreachable() {
        RedisBoardCacheStore unreachable =
                new RedisBoardCacheStore("127.0.0.1", 1, Duration.ofMillis(200), Duration.ofSeconds(60), 2, null);
        try {
            long started = System.nanoTime();
            assertThatThrownBy(() -> unreachable.find(ORGANIZATION, "a")).isInstanceOf(RuntimeException.class);
            assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(3));
        } finally {
            unreachable.close();
        }
    }
}
