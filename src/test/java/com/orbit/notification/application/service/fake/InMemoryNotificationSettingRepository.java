package com.orbit.notification.application.service.fake;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.orbit.notification.application.port.out.DuplicateNotificationSettingException;
import com.orbit.notification.application.port.out.NotificationSettingRepository;
import com.orbit.notification.domain.NotificationSetting;

/** 계정별 알림 설정의 메모리 구현. 새 설정을 같은 계정에 다시 만들면 계약대로 중복 예외를 던진다. */
public class InMemoryNotificationSettingRepository implements NotificationSettingRepository {

    private final Map<Long, NotificationSetting> byAccount = new HashMap<>();
    private long sequence;

    @Override
    public Optional<NotificationSetting> findByAccountId(Long accountId) {
        return Optional.ofNullable(byAccount.get(accountId)).map(InMemoryNotificationSettingRepository::copy);
    }

    @Override
    public NotificationSetting save(NotificationSetting setting) {
        if (setting.id().isEmpty() && byAccount.containsKey(setting.accountId())) {
            throw new DuplicateNotificationSettingException(new IllegalStateException("duplicate account"));
        }
        Long id = setting.id().orElseGet(() -> ++sequence);
        NotificationSetting stored = NotificationSetting.reconstitute(
                id,
                setting.accountId(),
                setting.workAssignmentEnabled(),
                setting.scheduleChangeEnabled(),
                setting.workCancellationEnabled());
        byAccount.put(setting.accountId(), stored);
        return copy(stored);
    }

    /** 다른 요청이 먼저 만든 설정처럼 행을 넣는다. */
    public void put(Long accountId, boolean workAssignment, boolean scheduleChange, boolean workCancellation) {
        byAccount.put(
                accountId,
                NotificationSetting.reconstitute(
                        ++sequence, accountId, workAssignment, scheduleChange, workCancellation));
    }

    private static NotificationSetting copy(NotificationSetting setting) {
        return NotificationSetting.reconstitute(
                setting.id().orElseThrow(),
                setting.accountId(),
                setting.workAssignmentEnabled(),
                setting.scheduleChangeEnabled(),
                setting.workCancellationEnabled());
    }
}
