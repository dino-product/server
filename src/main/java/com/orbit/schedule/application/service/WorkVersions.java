package com.orbit.schedule.application.service;

import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.application.port.out.ConcurrentWorkChangeException;
import com.orbit.schedule.application.port.out.WorkRepository;
import com.orbit.schedule.domain.Work;
import com.orbit.shared.error.BusinessException;

/**
 * 동시 조작 공통 절차([스케줄·배정] §7, 먼저 처리된 요청만 반영). 요청자가 본 작업 버전과 지금 버전이 다르거나, 저장 자리에서 다른 트랜잭션이 먼저 바꾼 것이
 * 드러나면 409(SCHEDULE-016)로 알린다. 버전 확인은 권한 확인 뒤, 입력 검증 전에 호출한다.
 */
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
}
