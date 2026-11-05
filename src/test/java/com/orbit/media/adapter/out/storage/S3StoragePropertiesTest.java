package com.orbit.media.adapter.out.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

@DisplayName("S3 저장소 설정")
class S3StoragePropertiesTest {

    @Test
    @DisplayName("제한 시간을 주지 않으면 저장소 호출은 5초(시도당 3초), 복사는 60초를 쓴다")
    void defaultsTimeouts() {
        S3StorageProperties properties = properties(null, null);

        assertThat(properties.apiCallTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(properties.apiCallAttemptTimeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(properties.copyTimeout()).isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    @DisplayName("복사 제한 시간이 다른 호출의 전체 제한 시간보다 짧으면 기동에 실패한다")
    void rejectsCopyTimeoutShorterThanApiCallTimeout() {
        assertThat(properties(Duration.ofSeconds(5), Duration.ofSeconds(5)).copyTimeout())
                .isEqualTo(Duration.ofSeconds(5));
        for (Duration invalid : new Duration[] {Duration.ofSeconds(4), Duration.ZERO, Duration.ofSeconds(-1)}) {
            assertThatThrownBy(() -> properties(Duration.ofSeconds(5), invalid))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("copy-timeout");
        }
    }

    @Test
    @DisplayName("app.media.storage.copy-timeout을 비우면 60초, 주면 그 값을 쓰고, 다른 호출의 전체 제한 시간보다 짧으면 기동하지 않는다")
    void bindsCopyTimeout() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withUserConfiguration(EnableProperties.class)
                .withPropertyValues("app.media.storage.bucket=bucket", "app.media.storage.region=ap-northeast-2");

        runner.withPropertyValues("app.media.storage.copy-timeout=").run(context -> assertThat(
                        context.getBean(S3StorageProperties.class).copyTimeout())
                .isEqualTo(Duration.ofSeconds(60)));
        runner.withPropertyValues("app.media.storage.copy-timeout=90s").run(context -> assertThat(
                        context.getBean(S3StorageProperties.class).copyTimeout())
                .isEqualTo(Duration.ofSeconds(90)));
        runner.withPropertyValues("app.media.storage.api-call-timeout=10s", "app.media.storage.copy-timeout=9s")
                .run(context -> assertThat(context).hasFailed());
    }

    private static S3StorageProperties properties(Duration apiCallTimeout, Duration copyTimeout) {
        return new S3StorageProperties(
                "bucket", "ap-northeast-2", null, false, null, null, null, null, apiCallTimeout, null, copyTimeout);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(S3StorageProperties.class)
    static class EnableProperties {}
}
