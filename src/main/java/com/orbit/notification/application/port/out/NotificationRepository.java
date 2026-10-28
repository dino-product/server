package com.orbit.notification.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.orbit.notification.domain.Notification;
import com.orbit.notification.domain.NotificationType;

/** 알림 저장소. 돌려주는 알림은 저장 상태와 분리된 사본이라, 바꾼 뒤 {@link #save}하지 않으면 저장되지 않는다. */
public interface NotificationRepository {

    /** 새 알림이면 식별자를 붙여 저장하고, 저장된 알림이면 읽음·숨김 상태를 옮긴다. */
    Notification save(Notification notification);

    /** 숨긴 알림도 찾는다. */
    Optional<Notification> findById(Long notificationId);

    /** 이 계정이 이 작업에 대해 받은 알림 전부(숨긴 알림 포함). */
    List<Notification> listReceivedForWork(Long accountId, Long workId);

    /**
     * 같은 일로 만든 알림(받는 계정·종류·작업·알림 시각이 모두 같음)이 이미 있는지 본다. 숨긴 알림도 센다. 이벤트가 다시 전달됐을 때 알림을 또 만들지 않는 데 쓴다.
     */
    boolean existsReceived(Long accountId, NotificationType type, Long workId, Instant notifiedAt);

    /**
     * 이 계정이 같은 참여 요청에 대해 같은 종류의 알림을 이미 받았는지 본다. 숨긴 알림도 센다. 참여 요청 결과 이벤트가 다시 전달됐을 때 알림을 또 만들지 않는 데
     * 쓴다.
     */
    boolean existsReceivedForParticipationRequest(Long accountId, NotificationType type, Long participationRequestId);
}
