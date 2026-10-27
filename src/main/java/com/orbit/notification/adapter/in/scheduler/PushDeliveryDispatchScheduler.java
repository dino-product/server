package com.orbit.notification.adapter.in.scheduler;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.orbit.notification.application.port.in.command.DispatchPushDeliveriesUseCase;

/** 기사 앱 푸시 발신 대기열을 주기적으로 처리한다. 앞선 실행이 끝난 뒤 간격을 두고 다시 돈다. */
@Component
@ConditionalOnProperty(prefix = "app.notification.push-delivery", name = "enabled", havingValue = "true")
class PushDeliveryDispatchScheduler {

    private final DispatchPushDeliveriesUseCase dispatchPushDeliveries;
    private final PushDeliveryProperties properties;

    PushDeliveryDispatchScheduler(
            DispatchPushDeliveriesUseCase dispatchPushDeliveries, PushDeliveryProperties properties) {
        this.dispatchPushDeliveries = dispatchPushDeliveries;
        this.properties = properties;
    }

    @Scheduled(
            fixedDelayString = "${app.notification.push-delivery.interval}",
            initialDelayString = "${app.notification.push-delivery.interval}")
    void dispatch() {
        dispatchPushDeliveries.dispatch(properties.toCommand());
    }
}
