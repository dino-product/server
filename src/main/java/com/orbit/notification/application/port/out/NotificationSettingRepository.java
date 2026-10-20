package com.orbit.notification.application.port.out;

import java.util.Optional;

import com.orbit.notification.domain.NotificationSetting;

/** 계정별 알림 설정 저장소. 계정마다 한 행이며, 바꾼 적 없는 계정은 행이 없다. */
public interface NotificationSettingRepository {

    Optional<NotificationSetting> findByAccountId(Long accountId);

    /**
     * 새 설정이면 만들고, 저장된 설정이면 수신 여부를 옮긴다.
     *
     * @throws DuplicateNotificationSettingException 같은 계정의 설정이 다른 요청으로 먼저 만들어졌을 때
     */
    NotificationSetting save(NotificationSetting setting);
}
