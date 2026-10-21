package com.orbit.notification.application.service.fake;

import java.util.ArrayList;
import java.util.List;

import com.orbit.notification.application.port.out.PushNotificationPort;
import com.orbit.notification.domain.Notification;

/** 푸시를 보내지 않고 보낸 알림만 기록한다. */
public class RecordingPushNotificationPort implements PushNotificationPort {

    private final List<Notification> sent = new ArrayList<>();

    @Override
    public void send(Notification notification) {
        if (notification.id().isEmpty()) {
            throw new IllegalArgumentException("only saved notifications can be pushed");
        }
        sent.add(notification);
    }

    public List<Notification> sent() {
        return List.copyOf(sent);
    }
}
