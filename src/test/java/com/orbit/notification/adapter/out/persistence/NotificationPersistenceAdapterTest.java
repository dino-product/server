package com.orbit.notification.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.orbit.notification.application.port.out.NotificationQueryPort.VisibleNotifications;
import com.orbit.notification.domain.Notification;
import com.orbit.notification.domain.NotificationStatus;
import com.orbit.notification.domain.NotificationType;
import com.orbit.support.PersistenceAdapterTestSupport;

/**
 * 실제 PostgreSQL에 저장·조회하며 NotificationRepository·NotificationQueryPort 계약을 확인한다. 클래스 트랜잭션을 끄고 조작마다 트랜잭션을 열어
 * 커밋하므로 "저장하지 않은 변경은 커밋돼도 남지 않는다"와 동시 수정을 실제 커밋 경계에서 본다. 커밋한 행이 남으므로 계정 식별자를 테스트마다 달리해(9_0xx)
 * 같은 DB를 쓰는 다른 테스트의 행과 섞이지 않게 한다.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("알림 저장·조회")
class NotificationPersistenceAdapterTest extends PersistenceAdapterTestSupport {

    private static final Instant NOTIFIED_AT = Instant.parse("2026-10-19T01:00:00.123456Z");

    @Autowired
    private SpringDataNotificationRepository repository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private NotificationPersistenceAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new NotificationPersistenceAdapter(repository);
    }

    @Test
    @DisplayName("새 알림을 저장하면 식별자와 함께 같은 값으로 복원된다")
    void savesAndRestoresNotification() {
        Notification saved = inTx(() -> adapter.save(Notification.create(
                9_001L, 1L, NotificationType.WORK_REJECTED, 10L, "담당 기사가 [보일러 점검] 작업을 거절했습니다. 사유: 기타", NOTIFIED_AT)));

        assertThat(find(saved.id().orElseThrow())).satisfies(found -> {
            assertThat(found.recipientAccountId()).isEqualTo(9_001L);
            assertThat(found.organizationId()).isEqualTo(1L);
            assertThat(found.type()).isEqualTo(NotificationType.WORK_REJECTED);
            assertThat(found.workId()).contains(10L);
            assertThat(found.message()).isEqualTo("담당 기사가 [보일러 점검] 작업을 거절했습니다. 사유: 기타");
            assertThat(found.notifiedAt()).isEqualTo(NOTIFIED_AT);
            assertThat(found.status()).isEqualTo(NotificationStatus.UNREAD);
            assertThat(found.readAt()).isEmpty();
            assertThat(found.isHidden()).isFalse();
        });
    }

    @Test
    @DisplayName("읽음·숨김을 저장하면 그 상태로 복원되고 저장하지 않은 변경은 커밋돼도 남지 않는다")
    void savesReadAndHiddenStateOnlyWhenSaved() {
        Long id = save(9_002L, 10L, NOTIFIED_AT);

        inTx(() -> {
            Notification unsaved = adapter.findById(id).orElseThrow();
            unsaved.markRead(NOTIFIED_AT.plusSeconds(60));
            unsaved.hideIfNotifiedBy(NOTIFIED_AT);
            return unsaved;
        });
        assertThat(find(id)).satisfies(found -> {
            assertThat(found.isRead()).isFalse();
            assertThat(found.isHidden()).isFalse();
        });

        inTx(() -> {
            Notification changed = adapter.findById(id).orElseThrow();
            changed.markRead(NOTIFIED_AT.plusSeconds(60));
            changed.hideIfNotifiedBy(NOTIFIED_AT);
            return adapter.save(changed);
        });

        assertThat(find(id)).satisfies(found -> {
            assertThat(found.status()).isEqualTo(NotificationStatus.READ);
            assertThat(found.readAt()).contains(NOTIFIED_AT.plusSeconds(60));
            assertThat(found.isHidden()).isTrue();
        });
    }

    @Test
    @DisplayName("읽음 처리 도중 다른 트랜잭션이 같은 알림을 숨기고 커밋해도 읽음과 숨김이 함께 남는다")
    void keepsConcurrentHideWhenReadIsSavedLater() {
        Long id = save(9_007L, 10L, NOTIFIED_AT);
        TransactionTemplate requiresNew = new TransactionTemplate(transactionManager);
        requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        inTx(() -> {
            Notification reading = adapter.findById(id).orElseThrow();
            reading.markRead(NOTIFIED_AT.plusSeconds(60));
            requiresNew.execute(status -> {
                Notification hiding = adapter.findById(id).orElseThrow();
                hiding.hideIfNotifiedBy(NOTIFIED_AT);
                return adapter.save(hiding);
            });
            return adapter.save(reading);
        });

        assertThat(find(id)).satisfies(found -> {
            assertThat(found.status()).isEqualTo(NotificationStatus.READ);
            assertThat(found.isHidden()).isTrue();
        });
    }

    @Test
    @DisplayName("목록은 본인 알림을 알림 시각 최신순(같으면 식별자 내림차순)으로 쪽을 나누고 숨긴 알림은 빼며 안 읽은 알림만 고를 수 있다")
    void listsVisibleNotificationsLatestFirst() {
        Long accountId = 9_003L;
        Long oldest = save(accountId, 10L, NOTIFIED_AT);
        Long sameTimeFirst = save(accountId, 11L, NOTIFIED_AT.plusSeconds(60));
        Long sameTimeSecond = save(accountId, 12L, NOTIFIED_AT.plusSeconds(60));
        Long hidden = save(accountId, 13L, NOTIFIED_AT.plusSeconds(120));
        inTx(() -> {
            Notification toHide = adapter.findById(hidden).orElseThrow();
            toHide.hideIfNotifiedBy(NOTIFIED_AT.plusSeconds(120));
            adapter.save(toHide);
            Notification toRead = adapter.findById(sameTimeSecond).orElseThrow();
            toRead.markRead(NOTIFIED_AT.plusSeconds(180));
            return adapter.save(toRead);
        });
        save(9_004L, 10L, NOTIFIED_AT.plusSeconds(240));

        VisibleNotifications firstPage = inTx(() -> adapter.listVisible(accountId, false, 0, 2));
        VisibleNotifications secondPage = inTx(() -> adapter.listVisible(accountId, false, 1, 2));
        VisibleNotifications unread = inTx(() -> adapter.listVisible(accountId, true, 0, 20));

        assertThat(firstPage.notifications())
                .extracting(notification -> notification.id().orElseThrow())
                .containsExactly(sameTimeSecond, sameTimeFirst);
        assertThat(firstPage.totalCount()).isEqualTo(3);
        assertThat(secondPage.notifications())
                .extracting(notification -> notification.id().orElseThrow())
                .containsExactly(oldest);
        assertThat(unread.notifications())
                .extracting(notification -> notification.id().orElseThrow())
                .containsExactly(sameTimeFirst, oldest);
        assertThat(unread.totalCount()).isEqualTo(2);
        assertThat(inTx(() -> adapter.countVisibleUnread(accountId))).isEqualTo(2);
    }

    @Test
    @DisplayName("계정이 한 작업에 대해 받은 알림을 숨긴 알림까지 모두 찾는다")
    void listsNotificationsReceivedForWork() {
        Long accountId = 9_005L;
        Long first = save(accountId, 10L, NOTIFIED_AT);
        Long second = save(accountId, 10L, NOTIFIED_AT.plusSeconds(60));
        inTx(() -> {
            Notification toHide = adapter.findById(second).orElseThrow();
            toHide.hideIfNotifiedBy(NOTIFIED_AT.plusSeconds(60));
            return adapter.save(toHide);
        });
        save(accountId, 11L, NOTIFIED_AT);
        save(9_006L, 10L, NOTIFIED_AT);

        assertThat(inTx(() -> adapter.listReceivedForWork(accountId, 10L)))
                .extracting(notification -> notification.id().orElseThrow())
                .containsExactly(first, second);
    }

    private Long save(Long accountId, Long workId, Instant notifiedAt) {
        return inTx(() -> adapter.save(
                        Notification.create(accountId, 1L, NotificationType.WORK_ASSIGNED, workId, "문구", notifiedAt)))
                .id()
                .orElseThrow();
    }

    private Notification find(Long id) {
        return inTx(() -> adapter.findById(id)).orElseThrow();
    }

    private <T> T inTx(Supplier<T> action) {
        return new TransactionTemplate(transactionManager).execute(status -> action.get());
    }
}
