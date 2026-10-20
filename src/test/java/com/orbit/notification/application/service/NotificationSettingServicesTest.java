package com.orbit.notification.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orbit.notification.application.error.NotificationErrorCode;
import com.orbit.notification.application.port.in.command.dto.ChangeNotificationSettingCommand;
import com.orbit.notification.application.port.in.query.dto.GetNotificationSettingQuery;
import com.orbit.notification.application.port.in.query.dto.NotificationSettingInfo;
import com.orbit.notification.application.port.out.DuplicateNotificationSettingException;
import com.orbit.notification.application.port.out.NotificationSettingRepository;
import com.orbit.notification.application.service.fake.InMemoryNotificationSettingRepository;
import com.orbit.notification.domain.NotificationSetting;
import com.orbit.shared.error.BusinessException;

@DisplayName("알림 설정 조회·저장")
class NotificationSettingServicesTest {

    private static final Long ACCOUNT_ID = 7L;

    private final InMemoryNotificationSettingRepository settings = new InMemoryNotificationSettingRepository();
    private final GetNotificationSettingService getService = new GetNotificationSettingService(settings);
    private final ChangeNotificationSettingService changeService = new ChangeNotificationSettingService(settings);

    @Test
    @DisplayName("바꾼 적이 없으면 모두 켜짐을 돌려주고 행을 만들지 않는다")
    void returnsDefaultsWithoutSavedSetting() {
        assertThat(getService.get(new GetNotificationSettingQuery(ACCOUNT_ID)))
                .isEqualTo(new NotificationSettingInfo(true, true, true));
        assertThat(settings.findByAccountId(ACCOUNT_ID)).isEmpty();
    }

    @Test
    @DisplayName("처음 바꾸면 설정을 만들고 다시 바꾸면 같은 설정을 고친다")
    void createsThenUpdatesSetting() {
        changeService.change(new ChangeNotificationSettingCommand(ACCOUNT_ID, false, true, true));
        Long createdId = settings.findByAccountId(ACCOUNT_ID).orElseThrow().id().orElseThrow();

        changeService.change(new ChangeNotificationSettingCommand(ACCOUNT_ID, true, false, false));

        assertThat(getService.get(new GetNotificationSettingQuery(ACCOUNT_ID)))
                .isEqualTo(new NotificationSettingInfo(true, false, false));
        assertThat(settings.findByAccountId(ACCOUNT_ID).orElseThrow().id()).contains(createdId);
        assertThat(getService.get(new GetNotificationSettingQuery(8L)))
                .isEqualTo(new NotificationSettingInfo(true, true, true));
    }

    @Test
    @DisplayName("같은 계정의 설정이 동시에 먼저 만들어지면 409로 알린다")
    void reportsConcurrentFirstSaveAsBusy() {
        NotificationSettingRepository racing = new NotificationSettingRepository() {
            @Override
            public Optional<NotificationSetting> findByAccountId(Long accountId) {
                return Optional.empty();
            }

            @Override
            public NotificationSetting save(NotificationSetting setting) {
                throw new DuplicateNotificationSettingException(new IllegalStateException("unique violation"));
            }
        };

        assertThatThrownBy(() -> new ChangeNotificationSettingService(racing)
                        .change(new ChangeNotificationSettingCommand(ACCOUNT_ID, false, false, false)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(NotificationErrorCode.NOTIFICATION_SETTING_BUSY));
    }
}
