package com.orbit.notification.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.orbit.notification.application.port.out.DuplicateNotificationSettingException;
import com.orbit.notification.domain.NotificationSetting;
import com.orbit.support.PersistenceAdapterTestSupport;

/**
 * 실제 PostgreSQL에 저장·조회하며 NotificationSettingRepository 계약을 확인한다. 클래스 트랜잭션을 끄고 조작마다 트랜잭션을 열어 커밋한다. 커밋한 행이
 * 남으므로 계정 식별자를 테스트마다 달리해(9_1xx) 같은 DB를 쓰는 다른 테스트의 행과 섞이지 않게 한다.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("알림 설정 저장")
class NotificationSettingPersistenceAdapterTest extends PersistenceAdapterTestSupport {

    @Autowired
    private SpringDataNotificationSettingRepository repository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private NotificationSettingPersistenceAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new NotificationSettingPersistenceAdapter(repository);
    }

    @Test
    @DisplayName("처음 저장하면 계정의 설정이 생기고 다시 저장하면 같은 행의 수신 여부만 바뀐다")
    void createsThenUpdatesSetting() {
        NotificationSetting created = NotificationSetting.defaults(9_101L);
        created.change(false, true, false);
        NotificationSetting saved = inTx(() -> adapter.save(created));

        NotificationSetting found = find(9_101L).orElseThrow();
        assertThat(found.id()).isEqualTo(saved.id()).isPresent();
        assertThat(found.workAssignmentEnabled()).isFalse();
        assertThat(found.scheduleChangeEnabled()).isTrue();
        assertThat(found.workCancellationEnabled()).isFalse();

        found.change(true, false, true);
        inTx(() -> adapter.save(found));

        assertThat(find(9_101L)).hasValueSatisfying(updated -> {
            assertThat(updated.id()).isEqualTo(saved.id());
            assertThat(updated.workAssignmentEnabled()).isTrue();
            assertThat(updated.scheduleChangeEnabled()).isFalse();
            assertThat(updated.workCancellationEnabled()).isTrue();
        });
        assertThat(find(9_102L)).isEmpty();
    }

    @Test
    @DisplayName("찾은 설정을 바꾸고 저장하지 않으면 커밋돼도 남지 않는다")
    void keepsSettingUnchangedWhenNotSaved() {
        inTx(() -> adapter.save(NotificationSetting.defaults(9_104L)));

        inTx(() -> {
            NotificationSetting unsaved = adapter.findByAccountId(9_104L).orElseThrow();
            unsaved.change(false, false, false);
            return unsaved;
        });

        assertThat(find(9_104L)).hasValueSatisfying(found -> {
            assertThat(found.workAssignmentEnabled()).isTrue();
            assertThat(found.scheduleChangeEnabled()).isTrue();
            assertThat(found.workCancellationEnabled()).isTrue();
        });
    }

    @Test
    @DisplayName("같은 계정의 새 설정을 또 만들면 계정당 한 행 제약을 중복 예외로 알린다")
    void reportsDuplicateAccountSetting() {
        inTx(() -> adapter.save(NotificationSetting.defaults(9_103L)));

        assertThatThrownBy(() -> inTx(() -> adapter.save(NotificationSetting.defaults(9_103L))))
                .isInstanceOf(DuplicateNotificationSettingException.class);
    }

    private Optional<NotificationSetting> find(Long accountId) {
        return inTx(() -> adapter.findByAccountId(accountId));
    }

    private <T> T inTx(Supplier<T> action) {
        return new TransactionTemplate(transactionManager).execute(status -> action.get());
    }
}
