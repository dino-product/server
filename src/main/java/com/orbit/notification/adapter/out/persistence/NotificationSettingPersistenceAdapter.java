package com.orbit.notification.adapter.out.persistence;

import java.util.Optional;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import com.orbit.notification.application.port.out.DuplicateNotificationSettingException;
import com.orbit.notification.application.port.out.NotificationSettingRepository;
import com.orbit.notification.domain.NotificationSetting;

/** 새 설정은 계정당 한 행 유일 제약 위반을 그 자리에서 알 수 있도록 바로 flush한다. */
@Repository
class NotificationSettingPersistenceAdapter implements NotificationSettingRepository {

    private final SpringDataNotificationSettingRepository repository;

    NotificationSettingPersistenceAdapter(SpringDataNotificationSettingRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<NotificationSetting> findByAccountId(Long accountId) {
        return repository.findByAccountId(accountId).map(NotificationSettingJpaEntity::toDomain);
    }

    @Override
    public NotificationSetting save(NotificationSetting setting) {
        if (setting.id().isPresent()) {
            NotificationSettingJpaEntity entity = repository
                    .findById(setting.id().get())
                    .orElseThrow(() -> new IllegalStateException("notification setting to update must exist"));
            entity.update(setting);
            return entity.toDomain();
        }
        try {
            return repository
                    .saveAndFlush(NotificationSettingJpaEntity.from(setting))
                    .toDomain();
        } catch (DataIntegrityViolationException exception) {
            if (exception.getCause() instanceof ConstraintViolationException violation
                    && NotificationSettingJpaEntity.MEMBER_UNIQUE.equalsIgnoreCase(violation.getConstraintName())) {
                throw new DuplicateNotificationSettingException(exception);
            }
            throw exception;
        }
    }
}
