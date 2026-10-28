package com.orbit.notification.adapter.out.fcm;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

import com.orbit.notification.application.port.out.PushSender;

@DisplayName("환경별 기사 앱 푸시 발신기")
class PushSenderSelectionTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ScanPushSenders.class)
            .withPropertyValues(
                    "app.notification.push.fcm.api-uri=https://fcm.googleapis.com",
                    "app.notification.push.fcm.timeout=PT5S");

    private FcmStub stub;

    @BeforeEach
    void setUp() throws Exception {
        stub = new FcmStub();
    }

    @AfterEach
    void tearDown() {
        stub.close();
    }

    @ParameterizedTest
    // dev는 프로필 그룹으로 deployed와 함께 켜진다(application.yml).
    @ValueSource(strings = {"local", "test", "dev", "dev,deployed"})
    @DisplayName("FCM을 끄면 로컬·테스트·개발 서버는 보내지 않고 기록만 하는 발신기를 쓴다")
    void usesLoggingSenderInLocalTestAndDevWhenFcmIsDisabled(String profiles) {
        runner.withPropertyValues("spring.profiles.active=" + profiles, "app.notification.push.fcm.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(PushSender.class);
                    assertThat(context.getBean(PushSender.class).getClass().getSimpleName())
                            .isEqualTo("LoggingPushSender");
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"prod", "prod,deployed", "prod,dev", "prod,local", "prod,test", "deployed", "staging", ""})
    @DisplayName("FCM을 끄면 운영과 이름 없는·그 밖의 환경에는 발신기가 없어 기동하지 않는다")
    void registersNoSenderElsewhereWhenFcmIsDisabled(String profiles) {
        runner.withPropertyValues("spring.profiles.active=" + profiles)
                .run(context -> assertThat(context).doesNotHaveBean(PushSender.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"prod", "prod,deployed", "dev,deployed", "local", ""})
    @DisplayName("FCM을 켜면 어느 환경이든 FCM 발신기 하나만 쓴다")
    void usesOnlyFcmSenderWhenEnabled(String profiles) {
        runner.withPropertyValues(
                        "spring.profiles.active=" + profiles,
                        "app.notification.push.fcm.enabled=true",
                        "app.notification.push.fcm.credentials=" + stub.credentials())
                .run(context -> {
                    assertThat(context).hasSingleBean(PushSender.class);
                    assertThat(context.getBean(PushSender.class)).isInstanceOf(FcmPushSender.class);
                });
    }

    @Test
    @DisplayName("FCM을 켰는데 자격 증명이 없거나 틀리면 기동하지 않는다")
    void failsToStartWhenEnabledWithoutValidCredentials() {
        runner.withPropertyValues("spring.profiles.active=prod", "app.notification.push.fcm.enabled=true")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues(
                        "spring.profiles.active=prod",
                        "app.notification.push.fcm.enabled=true",
                        "app.notification.push.fcm.credentials=not-a-service-account")
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(FcmProperties.class)
    // 모든 프로필에서 등록하는 저장 어댑터만 빼고 스캔해, 발신기 후보가 모두 검사 대상에 들어오게 한다.
    @ComponentScan(
            basePackages = "com.orbit.notification.adapter.out",
            excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = ".*\\.persistence\\..*"))
    static class ScanPushSenders {

        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }
    }
}
