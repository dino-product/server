package com.orbit.schedule.adapter.in.event;

import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.orbit.schedule.WorkChanged;
import com.orbit.schedule.application.port.in.command.InvalidateProgressBoardUseCase;

import lombok.extern.slf4j.Slf4j;

/**
 * 작업이 저장되면 커밋 뒤 저장해 둔 진행 보드를 버린다. 일반 이벤트 리스너로 발행 트랜잭션 안에서 받아 커밋 뒤 동작을 걸고, 롤백되면 버리지 않는다.
 *
 * <ul>
 *   <li>커밋 뒤 동작은 가장 앞 순서({@link Ordered#HIGHEST_PRECEDENCE})로, 요청 스레드에서 끝까지 실행한다. realtime의 신호 발행(기본 순서)보다 먼저 끝나야
 *       신호를 받고 바로 다시 조회한 화면이 옛 보드를 받지 않는다. 트랜잭션 밖에서 발행하면 바로 버리며, 리스너 자체도 가장 앞 순서라 realtime 리스너보다 먼저 돈다.
 *   <li>{@code @TransactionalEventListener}를 쓰지 않는다. Spring Modulith가 그런 리스너를 이벤트 발행 기록으로 감싸 별도 트랜잭션을 쓰기 때문이다(ADR-004).
 *   <li>발행 트랜잭션 안에서 동기로 돌므로 어떤 예외도 밖으로 던지지 않는다. 던지면 발행한 쪽의 저장이 롤백되거나 끝난 커밋이 실패로 보인다.
 * </ul>
 */
@Slf4j
@Component
class ProgressBoardCacheInvalidationListener {

    private final InvalidateProgressBoardUseCase invalidateUseCase;

    ProgressBoardCacheInvalidationListener(InvalidateProgressBoardUseCase invalidateUseCase) {
        this.invalidateUseCase = invalidateUseCase;
    }

    @EventListener
    @Order(Ordered.HIGHEST_PRECEDENCE)
    void on(WorkChanged event) {
        Long organizationId = event.organizationId();
        try {
            if (!TransactionSynchronizationManager.isSynchronizationActive()) {
                // 트랜잭션 밖에서 발행했으면 변경이 이미 반영됐다.
                invalidate(organizationId);
                return;
            }
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public int getOrder() {
                    return Ordered.HIGHEST_PRECEDENCE;
                }

                @Override
                public void afterCommit() {
                    invalidate(organizationId);
                }
            });
        } catch (RuntimeException exception) {
            log.warn(
                    "Progress board cache invalidation could not be scheduled: organizationId={}",
                    organizationId,
                    exception);
        }
    }

    private void invalidate(Long organizationId) {
        try {
            invalidateUseCase.invalidate(organizationId);
        } catch (RuntimeException exception) {
            log.warn("Progress board cache invalidation failed: organizationId={}", organizationId, exception);
        }
    }
}
