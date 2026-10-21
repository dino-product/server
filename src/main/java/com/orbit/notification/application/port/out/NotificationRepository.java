package com.orbit.notification.application.port.out;

import java.util.List;
import java.util.Optional;

import com.orbit.notification.domain.Notification;

/** 알림 저장소. 돌려주는 알림은 저장 상태와 분리된 사본이라, 바꾼 뒤 {@link #save}하지 않으면 저장되지 않는다. */
public interface NotificationRepository {

    /** 새 알림이면 식별자를 붙여 저장하고, 저장된 알림이면 읽음·숨김 상태를 옮긴다. */
    Notification save(Notification notification);

    /** 숨긴 알림도 찾는다. */
    Optional<Notification> findById(Long notificationId);

    /** 이 계정이 이 작업에 대해 받은 알림 전부(숨긴 알림 포함). */
    List<Notification> listReceivedForWork(Long accountId, Long workId);
}
