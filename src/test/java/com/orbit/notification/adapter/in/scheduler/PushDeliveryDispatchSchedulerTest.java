package com.orbit.notification.adapter.in.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.orbit.notification.application.port.in.command.DispatchPushDeliveriesUseCase;
import com.orbit.notification.application.port.in.command.dto.DispatchPushDeliveriesCommand;

class PushDeliveryDispatchSchedulerTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(EnableSchedulingConfiguration.class, PushDeliveryDispatchScheduler.class)
            .withBean(RecordingDispatcher.class, RecordingDispatcher::new)
            .withBean(
                    PushDeliveryProperties.class,
                    () -> new PushDeliveryProperties(7, Duration.ofMinutes(3), null, null))
            .withPropertyValues("app.notification.push-delivery.interval=PT0.05S");

    @Test
    void dispatchesPeriodicallyWithConfiguredSettingsWhenEnabled() {
        contextRunner
                .withPropertyValues("app.notification.push-delivery.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(PushDeliveryDispatchScheduler.class);
                    RecordingDispatcher dispatcher = context.getBean(RecordingDispatcher.class);

                    await().atMost(Duration.ofSeconds(5))
                            .until(() -> dispatcher.commands().size() >= 2);
                    DispatchPushDeliveriesCommand command =
                            dispatcher.commands().getFirst();
                    assertThat(command.batchSize()).isEqualTo(7);
                    assertThat(command.inFlightTimeout()).isEqualTo(Duration.ofMinutes(3));
                });
    }

    @Test
    void doesNotScheduleDispatchWhenDisabled() {
        contextRunner
                .withPropertyValues("app.notification.push-delivery.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(PushDeliveryDispatchScheduler.class);
                    RecordingDispatcher dispatcher = context.getBean(RecordingDispatcher.class);

                    await().during(Duration.ofMillis(300))
                            .atMost(Duration.ofSeconds(1))
                            .until(() -> dispatcher.commands().isEmpty());
                });
    }

    @EnableScheduling
    static class EnableSchedulingConfiguration {}

    static class RecordingDispatcher implements DispatchPushDeliveriesUseCase {

        private final List<DispatchPushDeliveriesCommand> commands = new CopyOnWriteArrayList<>();

        @Override
        public void dispatch(DispatchPushDeliveriesCommand command) {
            commands.add(command);
        }

        List<DispatchPushDeliveriesCommand> commands() {
            return commands;
        }
    }
}
