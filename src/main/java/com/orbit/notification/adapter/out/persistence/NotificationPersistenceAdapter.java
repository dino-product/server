package com.orbit.notification.adapter.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import com.orbit.notification.application.port.out.NotificationQueryPort;
import com.orbit.notification.application.port.out.NotificationRepository;
import com.orbit.notification.domain.Notification;
import com.orbit.notification.domain.NotificationStatus;

@Repository
class NotificationPersistenceAdapter implements NotificationRepository, NotificationQueryPort {

    private static final Sort LATEST_FIRST = Sort.by(Sort.Order.desc("notifiedAt"), Sort.Order.desc("id"));

    private final SpringDataNotificationRepository repository;

    NotificationPersistenceAdapter(SpringDataNotificationRepository repository) {
        this.repository = repository;
    }

    @Override
    public Notification save(Notification notification) {
        if (notification.id().isEmpty()) {
            return repository.save(NotificationJpaEntity.from(notification)).toDomain();
        }
        NotificationJpaEntity entity = repository
                .findById(notification.id().get())
                .orElseThrow(() -> new IllegalStateException("notification to update must exist"));
        entity.update(notification);
        return entity.toDomain();
    }

    @Override
    public Optional<Notification> findById(Long notificationId) {
        return repository.findById(notificationId).map(NotificationJpaEntity::toDomain);
    }

    @Override
    public List<Notification> listReceivedForWork(Long accountId, Long workId) {
        return repository.findByRecipientAccountIdAndWorkIdOrderByIdAsc(accountId, workId).stream()
                .map(NotificationJpaEntity::toDomain)
                .toList();
    }

    @Override
    public VisibleNotifications listVisible(Long accountId, boolean unreadOnly, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_SIZE || (long) page * size > MAX_OFFSET) {
            throw new IllegalArgumentException(
                    "size must be 1.." + MAX_SIZE + " and page must skip at most " + MAX_OFFSET + " notifications");
        }
        Pageable pageable = PageRequest.of(page, size, LATEST_FIRST);
        Page<NotificationJpaEntity> found = unreadOnly
                ? repository.findByRecipientAccountIdAndStatusAndHiddenFalse(
                        accountId, NotificationStatus.UNREAD, pageable)
                : repository.findByRecipientAccountIdAndHiddenFalse(accountId, pageable);
        return new VisibleNotifications(
                found.getContent().stream().map(NotificationJpaEntity::toDomain).toList(), found.getTotalElements());
    }

    @Override
    public long countVisibleUnread(Long accountId) {
        return repository.countByRecipientAccountIdAndStatusAndHiddenFalse(accountId, NotificationStatus.UNREAD);
    }
}
