package com.orbit.notification.application.service;

import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.notification.application.error.NotificationErrorCode;
import com.orbit.notification.application.port.in.command.ChangeNotificationSettingUseCase;
import com.orbit.notification.application.port.in.command.dto.ChangeNotificationSettingCommand;
import com.orbit.notification.application.port.out.DuplicateNotificationSettingException;
import com.orbit.notification.application.port.out.NotificationSettingRepository;
import com.orbit.notification.domain.NotificationSetting;
import com.orbit.shared.error.BusinessException;

/**
 * 요청 계정의 알림 설정을 바꾼다. 처음 바꾸면 설정 행을 만든다. 같은 계정이 처음 바꾸는 요청을 동시에 보내 행이 겹치면 늦은 요청은 409(NOTIFICATION-004)이고 다시
 * 보내면 저장된 행을 바꾼다.
 */
@Service
public class ChangeNotificationSettingService implements ChangeNotificationSettingUseCase {

    private final NotificationSettingRepository settingRepository;

    public ChangeNotificationSettingService(NotificationSettingRepository settingRepository) {
        this.settingRepository = settingRepository;
    }

    @Override
    @Transactional
    public void change(ChangeNotificationSettingCommand command) {
        Long accountId = Objects.requireNonNull(command.accountId(), "accountId must not be null");
        NotificationSetting setting =
                settingRepository.findByAccountId(accountId).orElseGet(() -> NotificationSetting.defaults(accountId));
        setting.change(
                command.workAssignmentEnabled(), command.scheduleChangeEnabled(), command.workCancellationEnabled());
        try {
            settingRepository.save(setting);
        } catch (DuplicateNotificationSettingException exception) {
            throw new BusinessException(NotificationErrorCode.NOTIFICATION_SETTING_BUSY, exception);
        }
    }
}
