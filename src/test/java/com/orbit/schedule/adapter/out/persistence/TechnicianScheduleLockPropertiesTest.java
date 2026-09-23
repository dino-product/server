package com.orbit.schedule.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

@DisplayName("기사 일정 잠금 설정")
class TechnicianScheduleLockPropertiesTest {

    @Test
    @DisplayName("대기 한도가 없으면 2초를 쓴다")
    void defaultsToTwoSeconds() {
        assertThat(new TechnicianScheduleLockProperties(null).waitLimit()).isEqualTo(Duration.ofSeconds(2));
    }

    @Test
    @DisplayName("환경 변수를 비워 두거나 값을 주면 설정 바인딩이 기본값·지정값을 쓴다")
    void bindsEmptyAndGivenValues() {
        ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(EnableProperties.class);

        runner.withPropertyValues("app.schedule.technician-lock.wait-limit=").run(context -> assertThat(
                        context.getBean(TechnicianScheduleLockProperties.class).waitLimit())
                .isEqualTo(Duration.ofSeconds(2)));
        runner.withPropertyValues("app.schedule.technician-lock.wait-limit=500ms")
                .run(context -> assertThat(context.getBean(TechnicianScheduleLockProperties.class)
                                .waitLimit())
                        .isEqualTo(Duration.ofMillis(500)));
        runner.withPropertyValues("app.schedule.technician-lock.wait-limit=31s")
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(TechnicianScheduleLockProperties.class)
    static class EnableProperties {}

    @Test
    @DisplayName("대기 한도는 1ms 이상 30초 이하만 허용해, PostgreSQL이 받지 못하는 값으로 기동하지 않는다")
    void acceptsOnlyBoundedWaitLimit() {
        assertThat(new TechnicianScheduleLockProperties(Duration.ofMillis(1)).waitLimit())
                .isEqualTo(Duration.ofMillis(1));
        assertThat(new TechnicianScheduleLockProperties(Duration.ofSeconds(30)).waitLimit())
                .isEqualTo(Duration.ofSeconds(30));
        for (Duration invalid : new Duration[] {Duration.ZERO, Duration.ofNanos(999_999), Duration.ofSeconds(31)}) {
            assertThatThrownBy(() -> new TechnicianScheduleLockProperties(invalid))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
