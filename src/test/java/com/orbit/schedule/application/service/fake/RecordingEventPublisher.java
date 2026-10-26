package com.orbit.schedule.application.service.fake;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.context.ApplicationEventPublisher;

/** 서비스 단위 테스트용 ApplicationEventPublisher. 스캔되지 않도록 애너테이션을 붙이지 않는다. 발행하지 않고 발행한 이벤트를 순서대로 기록한다. */
public class RecordingEventPublisher implements ApplicationEventPublisher {

    private final List<Object> published = new ArrayList<>();

    @Override
    public void publishEvent(Object event) {
        published.add(Objects.requireNonNull(event, "event must not be null"));
    }

    /** 발행한 순서대로의 이벤트. */
    public List<Object> published() {
        return List.copyOf(published);
    }
}
