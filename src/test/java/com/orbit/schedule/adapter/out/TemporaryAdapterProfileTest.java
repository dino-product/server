package com.orbit.schedule.adapter.out;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import com.orbit.schedule.application.port.out.LoadActorPort;
import com.orbit.schedule.application.port.out.WorkRepository;

@DisplayName("임시 출력 어댑터 등록 프로필")
class TemporaryAdapterProfileTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(ScanTemporaryAdapters.class);

    @ParameterizedTest
    @ValueSource(strings = {"local", "test"})
    @DisplayName("로컬·테스트 프로필에서만 등록한다")
    void registersOnlyInLocalAndTest(String profile) {
        runner.withPropertyValues("spring.profiles.active=" + profile).run(context -> {
            assertThat(context).hasSingleBean(WorkRepository.class);
            assertThat(context).hasSingleBean(LoadActorPort.class);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"prod", "prod,local", "prod,test", "staging", ""})
    @DisplayName("운영과 이름 없는·그 밖의 환경에서는 등록하지 않는다")
    void doesNotRegisterElsewhere(String profiles) {
        runner.withPropertyValues("spring.profiles.active=" + profiles).run(context -> {
            assertThat(context).doesNotHaveBean(WorkRepository.class);
            assertThat(context).doesNotHaveBean(LoadActorPort.class);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @ComponentScan("com.orbit.schedule.adapter.out")
    static class ScanTemporaryAdapters {}
}
