package com.orbit.schedule.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.application.port.out.ConcurrentWorkChangeException;
import com.orbit.shared.error.BusinessException;

@DisplayName("저장 자리 버전 충돌의 다시 실행")
class WorkVersionsRetryTest {

    private final AtomicInteger runs = new AtomicInteger();

    @Test
    @DisplayName("충돌이 없으면 한 번만 실행한다")
    void runsOnceWithoutConflict() {
        WorkVersions.retryOnConflict(runs::incrementAndGet);

        assertThat(runs).hasValue(1);
    }

    @Test
    @DisplayName("첫 실행이 충돌하면 한 번 다시 실행해 그 결과로 끝낸다")
    void retriesOnceAfterConflict() {
        WorkVersions.retryOnConflict(() -> {
            if (runs.incrementAndGet() == 1) {
                throw conflict();
            }
        });

        assertThat(runs).hasValue(2);
    }

    @Test
    @DisplayName("다시 실행해도 충돌하면 더 실행하지 않고 변경된 정보라는 409다")
    void reportsWorkChangedWhenRetryConflictsAgain() {
        assertThatThrownBy(() -> WorkVersions.retryOnConflict(() -> {
                    runs.incrementAndGet();
                    throw conflict();
                }))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ScheduleErrorCode.WORK_CHANGED))
                .hasCauseInstanceOf(ConcurrentWorkChangeException.class);
        assertThat(runs).hasValue(2);
    }

    @Test
    @DisplayName("업무 오류는 다시 실행하지 않고 그대로 알린다")
    void doesNotRetryBusinessErrors() {
        assertThatThrownBy(() -> WorkVersions.retryOnConflict(() -> {
                    runs.incrementAndGet();
                    throw new BusinessException(ScheduleErrorCode.INVALID_WORK_STATE);
                }))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ScheduleErrorCode.INVALID_WORK_STATE));
        assertThat(runs).hasValue(1);
    }

    @Test
    @DisplayName("바깥 트랜잭션 안에서 충돌하면 같은 트랜잭션으로는 새로 읽지 못하므로 다시 실행하지 않고 409다")
    void doesNotRetryInsideCallerTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThatThrownBy(() -> WorkVersions.retryOnConflict(() -> {
                        runs.incrementAndGet();
                        throw conflict();
                    }))
                    .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                            .isEqualTo(ScheduleErrorCode.WORK_CHANGED));
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        assertThat(runs).hasValue(1);
    }

    private static ConcurrentWorkChangeException conflict() {
        return new ConcurrentWorkChangeException(new IllegalStateException("simulated concurrent change"));
    }
}
