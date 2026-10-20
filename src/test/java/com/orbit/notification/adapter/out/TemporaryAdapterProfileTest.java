package com.orbit.notification.adapter.out;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

import com.orbit.notification.application.port.out.PushNotificationPort;

@DisplayName("notification 임시 출력 어댑터 등록 프로필")
class TemporaryAdapterProfileTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(ScanTemporaryAdapters.class);

    @ParameterizedTest
    // dev는 프로필 그룹으로 deployed와 함께 켜진다(application.yml).
    @ValueSource(strings = {"local", "test", "dev", "dev,deployed"})
    @DisplayName("로컬·테스트·개발 서버 프로필에서만 등록한다")
    void registersOnlyInLocalTestAndDev(String profile) {
        runner.withPropertyValues("spring.profiles.active=" + profile)
                .run(context -> assertThat(context).hasSingleBean(PushNotificationPort.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"prod", "prod,deployed", "prod,dev", "prod,local", "prod,test", "deployed", "staging", ""})
    @DisplayName("운영과 이름 없는·그 밖의 환경에서는 등록하지 않는다")
    void doesNotRegisterElsewhere(String profiles) {
        runner.withPropertyValues("spring.profiles.active=" + profiles)
                .run(context -> assertThat(context).doesNotHaveBean(PushNotificationPort.class));
    }

    @Configuration(proxyBeanMethods = false)
    // 모든 프로필에서 등록하는 저장 어댑터만 빼고 스캔해, 새로 생기는 임시 어댑터도 검사 대상에 들어오게 한다.
    @ComponentScan(
            basePackages = "com.orbit.notification.adapter.out",
            excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = ".*\\.persistence\\..*"))
    static class ScanTemporaryAdapters {}
}
