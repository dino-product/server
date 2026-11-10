package com.orbit.notification.adapter.out.persistence;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.notification.application.port.out.NotificationDeliveryRepository;
import com.orbit.notification.domain.DeliveryStatus;
import com.orbit.notification.domain.NotificationDelivery;

/**
 * {@link NotificationDeliveryRepository}의 JPA 구현. 집기는 {@code FOR UPDATE SKIP LOCKED}로 다른 인스턴스가 잠근 행을 건너뛰고, 집기·결과
 * 저장은 부른 쪽 트랜잭션과 무관하게 새 트랜잭션으로 커밋한다. 결과 저장은 행을 잠근 뒤 집은 시점의 시도 횟수와 비교해 그 사이 다시 집힌 행을 덮어쓰지 않는다.
 */
@Repository
class NotificationDeliveryPersistenceAdapter implements NotificationDeliveryRepository {

    private final SpringDataNotificationDeliveryRepository repository;

    NotificationDeliveryPersistenceAdapter(SpringDataNotificationDeliveryRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public NotificationDelivery add(NotificationDelivery delivery) {
        return repository.save(NotificationDeliveryJpaEntity.from(delivery)).toDomain();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<NotificationDelivery> claimDue(Instant now, Instant leaseUntil, int limit) {
        return repository.lockDue(now, limit).stream()
                .map(entity -> {
                    NotificationDelivery claimed = entity.toDomain().claim(now, leaseUntil);
                    entity.apply(claimed);
                    return claimed;
                })
                .toList();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean saveResult(NotificationDelivery delivery) {
        Long id = delivery.id()
                .orElseThrow(() -> new IllegalArgumentException("delivery must be saved"))
                .value();
        NotificationDeliveryJpaEntity entity = repository
                .findLockedById(id)
                .orElseThrow(() -> new IllegalStateException("Cannot save result of unknown delivery: " + id));
        if (entity.status() != DeliveryStatus.IN_FLIGHT || entity.attempts() != delivery.attempts()) {
            return false;
        }
        entity.apply(delivery);
        return true;
    }
}
