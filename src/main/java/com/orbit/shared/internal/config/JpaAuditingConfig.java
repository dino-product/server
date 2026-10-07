package com.orbit.shared.internal.config;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA 감사 컬럼({@code created_at}, {@code updated_at})을 채우는 설정. 감사 시각은 시스템 시계가 아니라 {@link TimeConfig}의 주입 {@link Clock}에서
 * 가져와, 업무 시각과 같은 UTC·마이크로초 기준을 쓰고 테스트에서 고정할 수 있게 한다.
 */
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaAuditingConfig {

    @Bean
    public DateTimeProvider auditingDateTimeProvider(Clock clock) {
        return () -> Optional.of(Instant.now(clock));
    }
}
