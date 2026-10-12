package com.orbit.shared.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.orbit.shared.internal.config.JpaAuditingConfig;
import com.orbit.support.TestcontainersConfiguration;

/** 감사 컬럼이 주입 Clock의 시각으로 채워지고, 수정 때 created_at은 그대로, updated_at만 바뀌는지 실제 PostgreSQL로 확인한다. */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class, BaseTimeEntityTest.AdjustableClockConfig.class})
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("감사 컬럼 공통 Entity")
class BaseTimeEntityTest {

    private static final Instant CREATED = Instant.parse("2030-01-02T03:04:05.123456789Z");
    private static final Instant UPDATED = CREATED.plus(Duration.ofMinutes(10));

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private AdjustableClock clock;

    @Test
    @DisplayName("저장하면 생성·수정 시각을 주입 Clock의 마이크로초 시각으로 채운다")
    void fillsAuditColumnsFromInjectedClockOnPersist() {
        clock.set(CREATED);
        AuditedSampleJpaEntity entity = new AuditedSampleJpaEntity("before");

        entityManager.persist(entity);
        entityManager.flush();

        Instant expected = Instant.parse("2030-01-02T03:04:05.123456Z");
        assertThat(entity.createdAt()).isEqualTo(expected);
        assertThat(entity.updatedAt()).isEqualTo(expected);
    }

    @Test
    @DisplayName("수정하면 수정 시각만 바뀌고 생성 시각은 그대로다")
    void keepsCreatedAtAndMovesUpdatedAtOnModification() {
        clock.set(CREATED);
        AuditedSampleJpaEntity entity = new AuditedSampleJpaEntity("before");
        entityManager.persist(entity);
        entityManager.flush();
        Instant createdAt = entity.createdAt();

        clock.set(UPDATED);
        entity.relabel("after");
        entityManager.flush();
        entityManager.clear();

        AuditedSampleJpaEntity reloaded = entityManager.find(AuditedSampleJpaEntity.class, entity.id());
        assertThat(reloaded.createdAt()).isEqualTo(createdAt);
        assertThat(reloaded.updatedAt()).isEqualTo(Instant.parse("2030-01-02T03:14:05.123456Z"));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class AdjustableClockConfig {

        /** 운영의 {@code TimeConfig}처럼 마이크로초로 끊되, 테스트가 시각을 옮길 수 있는 Clock. */
        @Bean
        AdjustableClock clock() {
            return new AdjustableClock(CREATED);
        }
    }

    static final class AdjustableClock extends Clock {

        private volatile Instant instant;

        AdjustableClock(Instant instant) {
            set(instant);
        }

        void set(Instant newInstant) {
            this.instant = newInstant.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
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
            return instant;
        }
    }
}
