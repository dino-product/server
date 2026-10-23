package com.orbit.notification.application.service.fake;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.orbit.notification.application.port.out.NotificationQueryPort;
import com.orbit.notification.application.port.out.NotificationRepository;
import com.orbit.notification.domain.Notification;
import com.orbit.notification.domain.NotificationType;

/** 알림 저장소·조회의 메모리 구현. 저장·조회 모두 사본을 주고받아 저장하지 않은 변경은 남지 않는다. */
public class InMemoryNotificationRepository implements NotificationRepository, NotificationQueryPort {

    private static final Comparator<Notification> LATEST_FIRST = Comparator.comparing(Notification::notifiedAt)
            .thenComparing(notification -> notification.id().orElseThrow())
            .reversed();

    private final Map<Long, Notification> rows = new LinkedHashMap<>();
    private long sequence;

    @Override
    public Notification save(Notification notification) {
        Long id = notification.id().orElseGet(() -> ++sequence);
        if (notification.id().isPresent() && !rows.containsKey(id)) {
            throw new IllegalStateException("notification to update must exist");
        }
        rows.put(id, copy(notification, id));
        return copy(notification, id);
    }

    @Override
    public Optional<Notification> findById(Long notificationId) {
        return Optional.ofNullable(rows.get(notificationId)).map(found -> copy(found, notificationId));
    }

    @Override
    public List<Notification> listReceivedForWork(Long accountId, Long workId) {
        return all().stream()
                .filter(notification -> notification.isReceivedBy(accountId))
                .filter(notification ->
                        notification.workId().filter(workId::equals).isPresent())
                .toList();
    }

    @Override
    public boolean existsReceived(Long accountId, NotificationType type, Long workId, Instant notifiedAt) {
        return all().stream()
                .anyMatch(notification -> notification.isReceivedBy(accountId)
                        && notification.type() == type
                        && Objects.equals(notification.workId().orElse(null), workId)
                        && notification.notifiedAt().equals(notifiedAt));
    }

    @Override
    public VisibleNotifications listVisible(Long accountId, boolean unreadOnly, int page, int size) {
        List<Notification> matched = all().stream()
                .filter(notification -> notification.isReceivedBy(accountId))
                .filter(notification -> !notification.isHidden())
                .filter(notification -> !unreadOnly || !notification.isRead())
                .sorted(LATEST_FIRST)
                .toList();
        return new VisibleNotifications(
                matched.stream().skip((long) page * size).limit(size).toList(), matched.size());
    }

    @Override
    public long countVisibleUnread(Long accountId) {
        return all().stream()
                .filter(notification -> notification.isReceivedBy(accountId))
                .filter(notification -> !notification.isHidden() && !notification.isRead())
                .count();
    }

    /** 저장된 순서의 모든 알림 사본(숨긴 알림 포함). */
    public List<Notification> all() {
        List<Notification> copies = new ArrayList<>();
        rows.forEach((id, notification) -> copies.add(copy(notification, id)));
        return copies;
    }

    private static Notification copy(Notification notification, Long id) {
        return Notification.reconstitute(
                id,
                notification.recipientAccountId(),
                notification.organizationId(),
                notification.type(),
                notification.workId().orElse(null),
                notification.message(),
                notification.notifiedAt(),
                notification.status(),
                notification.readAt().orElse(null),
                notification.isHidden());
    }
}
