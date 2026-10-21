package com.orbit.notification.application.service;

import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.notification.application.port.in.query.GetNotificationSettingUseCase;
import com.orbit.notification.application.port.in.query.dto.GetNotificationSettingQuery;
import com.orbit.notification.application.port.in.query.dto.NotificationSettingInfo;
import com.orbit.notification.application.port.out.NotificationSettingRepository;
import com.orbit.notification.domain.NotificationSetting;

/** 요청 계정의 알림 설정. 바꾼 적이 없으면 기본값(모두 켜짐)이다. */
@Service
public class GetNotificationSettingService implements GetNotificationSettingUseCase {

    private final NotificationSettingRepository settingRepository;

    public GetNotificationSettingService(NotificationSettingRepository settingRepository) {
        this.settingRepository = settingRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationSettingInfo get(GetNotificationSettingQuery query) {
        Long accountId = Objects.requireNonNull(query.accountId(), "accountId must not be null");
        return NotificationSettingInfo.from(
                settingRepository.findByAccountId(accountId).orElseGet(() -> NotificationSetting.defaults(accountId)));
    }
}
