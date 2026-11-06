package com.orbit.schedule.application.service;

import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.application.port.out.ConcurrentWorkChangeException;
import com.orbit.schedule.application.port.out.WorkRepository;
import com.orbit.schedule.domain.Work;
import com.orbit.shared.error.BusinessException;

import lombok.extern.slf4j.Slf4j;

/**
 * 동시 조작 공통 절차([스케줄·배정] §7, 먼저 처리된 요청만 반영). 요청자가 본 작업 버전과 지금 버전이 다르거나, 저장 자리에서 다른 트랜잭션이 먼저 바꾼 것이
 * 드러나면 409(SCHEDULE-016)로 알린다. 버전 확인은 권한 확인 뒤, 입력 검증 전에 호출한다. 본 버전을 받지 않는 유즈케이스는 저장 자리의 충돌을
 * {@link #retryOnConflict}로 한 번 다시 실행해, 순서대로 들어온 것과 같은 결과를 준다.
 */
@Slf4j
final class WorkVersions {

    private WorkVersions() {}

    /** 요청자가 본 버전. 없거나 음수면 400(SCHEDULE-003), 지금 버전과 다르면 409(SCHEDULE-016). */
    static void require(Work work, Long expectedVersion) {
        if (expectedVersion == null || expectedVersion < 0) {
            throw new BusinessException(ScheduleErrorCode.INVALID_WORK_INPUT);
        }
        DomainRuleViolations.run(() -> work.requireVersion(expectedVersion));
    }

    /** 저장하고, 이 트랜잭션이 읽은 뒤 다른 트랜잭션이 같은 작업을 바꿨으면 409(SCHEDULE-016)로 알린다. */
    static Work save(WorkRepository workRepository, Work work) {
        try {
            return workRepository.save(work);
        } catch (ConcurrentWorkChangeException e) {
            throw new BusinessException(ScheduleErrorCode.WORK_CHANGED, e);
        }
    }

    /**
     * 트랜잭션 부분을 실행하고, 저장 자리에서 다른 트랜잭션이 먼저 바꾼 것이 드러나면 새 트랜잭션에서 한 번 다시 실행해 다시 읽은 상태로 결과를 정한다(중복 전송이면
     * 성공, 취소됐으면 상태 오류 등). 다시 실행해도 충돌하면 409(SCHEDULE-016)다. 충돌한 첫 시도는 롤백되고, 저장에서 멈춰 그 뒤의 사진 연결·이벤트 발행에
     * 닿지 않는다(커밋 뒤에만 동작하는 리스너도 돌지 않는다).
     *
     * <p>{@code transaction}은 트랜잭션 부분 빈(*Transaction)의 호출이어야 하고, 트랜잭션 밖에서 불러야 한다. 바깥 트랜잭션 안이면 충돌한 트랜잭션은 롤백만
     * 할 수 있고 같은 영속성 컨텍스트로는 새로 읽지 못하므로, 다시 실행하지 않고 409로 알린다.
     */
    static void retryOnConflict(Runnable transaction) {
        try {
            transaction.run();
            return;
        } catch (ConcurrentWorkChangeException first) {
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                throw new BusinessException(ScheduleErrorCode.WORK_CHANGED, first);
            }
            log.debug("Work changed by another transaction; retrying once", first);
        }
        try {
            transaction.run();
        } catch (ConcurrentWorkChangeException second) {
            throw new BusinessException(ScheduleErrorCode.WORK_CHANGED, second);
        }
    }
}
