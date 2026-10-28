package com.orbit.notification.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.orbit.notification.domain.PushDevice;
import com.orbit.notification.domain.PushPlatform;
import com.orbit.support.PersistenceAdapterTestSupport;

/**
 * 실제 PostgreSQL에 저장·조회하며 PushDeviceRepository 계약을 확인한다. 클래스 트랜잭션을 끄고 조작마다 트랜잭션을 열어 커밋한다. 커밋한 행이 남으므로 토큰을
 * 테스트마다 새로 만들어 같은 DB를 쓰는 다른 테스트의 행과 섞이지 않게 한다.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("앱 푸시 기기 저장")
class PushDevicePersistenceAdapterTest extends PersistenceAdapterTestSupport {

    private static final Instant FIRST = Instant.parse("2026-10-28T01:00:00Z");
    private static final Instant SECOND = Instant.parse("2026-10-28T02:00:00Z");

    @Autowired
    private SpringDataPushDeviceRepository repository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private PushDevicePersistenceAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new PushDevicePersistenceAdapter(repository);
    }

    @Test
    @DisplayName("새 토큰은 계정의 기기로 저장하고 같은 토큰을 다시 등록하면 같은 행을 바꾼다")
    void registersThenReplacesSameTokenRow() {
        String token = newToken();
        PushDevice created = inTx(() -> adapter.register(PushDevice.register(9_201L, PushPlatform.IOS, token, FIRST)));

        assertThat(created.id()).isPresent();
        assertThat(created.accountId()).isEqualTo(9_201L);
        assertThat(created.platform()).isEqualTo(PushPlatform.IOS);
        assertThat(created.token()).isEqualTo(token);
        assertThat(created.registeredAt()).isEqualTo(FIRST);

        PushDevice moved =
                inTx(() -> adapter.register(PushDevice.register(9_202L, PushPlatform.ANDROID, token, SECOND)));

        assertThat(moved.id()).isEqualTo(created.id());
        assertThat(moved.accountId()).isEqualTo(9_202L);
        assertThat(moved.platform()).isEqualTo(PushPlatform.ANDROID);
        assertThat(moved.registeredAt()).isEqualTo(SECOND);
        assertThat(rowsWithToken(token)).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 토큰을 동시에 등록해도 제약 위반 없이 한 행만 남는다")
    void keepsOneRowWhenSameTokenRegisteredConcurrently() throws Exception {
        String token = newToken();
        int registrations = 8;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(registrations);
        try {
            List<CompletableFuture<PushDevice>> futures = IntStream.range(0, registrations)
                    .mapToObj(index -> CompletableFuture.supplyAsync(
                            () -> {
                                await(start);
                                return inTx(() -> adapter.register(
                                        PushDevice.register(9_210L + index, PushPlatform.ANDROID, token, FIRST)));
                            },
                            executor))
                    .toList();
            start.countDown();

            List<Long> ids = futures.stream()
                    .map(future -> future.orTimeout(30, TimeUnit.SECONDS).join())
                    .map(device -> device.id().orElseThrow())
                    .distinct()
                    .toList();

            assertThat(ids).hasSize(1);
            assertThat(rowsWithToken(token)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("기기는 그 계정이 지울 때만 지워진다")
    void deletesOnlyOwnedDevice() {
        String token = newToken();
        Long deviceId = inTx(() -> adapter.register(PushDevice.register(9_221L, PushPlatform.IOS, token, FIRST)))
                .id()
                .orElseThrow();

        assertThat(inTx(() -> adapter.deleteOwnedBy(deviceId, 9_222L))).isFalse();
        assertThat(rowsWithToken(token)).isEqualTo(1);

        assertThat(inTx(() -> adapter.deleteOwnedBy(deviceId, 9_221L))).isTrue();
        assertThat(rowsWithToken(token)).isZero();
        assertThat(inTx(() -> adapter.deleteOwnedBy(deviceId, 9_221L))).isFalse();
    }

    private int rowsWithToken(String token) {
        return jdbcTemplate.queryForObject("select count(*) from push_devices where token = ?", Integer.class, token);
    }

    private static String newToken() {
        return "token-" + UUID.randomUUID();
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private <T> T inTx(Supplier<T> action) {
        return new TransactionTemplate(transactionManager).execute(status -> action.get());
    }
}
