package com.orbit.shared.internal.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.modulith.events.FailedEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;

import com.orbit.shared.internal.config.SchedulingConfig;

class FailedEventPublicationResubmitSchedulerTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    SchedulingConfig.class,
                    FailedEventPublicationResubmitter.class,
                    FailedEventPublicationResubmitScheduler.class)
            .withBean(CountingFailedEventPublications.class, CountingFailedEventPublications::new)
            .withBean(EventResubmissionProperties.class, () -> new EventResubmissionProperties(null, null))
            .withBean(Clock.class, Clock::systemUTC)
            .withPropertyValues("app.events.resubmission.interval=PT0.05S");

    @Test
    void resubmitsFailedPublicationsPeriodicallyWhenEnabled() {
        contextRunner.withPropertyValues("app.events.resubmission.enabled=true").run(context -> {
            assertThat(context).hasSingleBean(FailedEventPublicationResubmitScheduler.class);
            CountingFailedEventPublications publications = context.getBean(CountingFailedEventPublications.class);

            await().atMost(Duration.ofSeconds(5))
                    .alias("Moments 자동 설정 없이도 SchedulingConfig로 주기 재전송이 돈다")
                    .until(() -> publications.calls() >= 2);
        });
    }

    @Test
    void doesNotScheduleResubmissionWhenDisabled() {
        contextRunner
                .withPropertyValues("app.events.resubmission.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(FailedEventPublicationResubmitScheduler.class);
                    CountingFailedEventPublications publications =
                            context.getBean(CountingFailedEventPublications.class);

                    await().during(Duration.ofMillis(300))
                            .atMost(Duration.ofSeconds(1))
                            .until(() -> publications.calls() == 0);
                });
    }

    static class CountingFailedEventPublications implements FailedEventPublications {

        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public void resubmit(ResubmissionOptions options) {
            calls.incrementAndGet();
        }

        int calls() {
            return calls.get();
        }
    }
}
