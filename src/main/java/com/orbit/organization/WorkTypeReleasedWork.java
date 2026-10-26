package com.orbit.organization;

/**
 * 작업 유형 삭제로 유형 없음이 된 작업.
 *
 * @param workId 작업 식별자
 * @param technicianId 배정됨·작업전 작업의 담당 기사 계약 식별자. 등록(대기함) 작업이면 null
 */
public record WorkTypeReleasedWork(Long workId, Long technicianId) {

    public WorkTypeReleasedWork {
        if (workId == null || workId <= 0) {
            throw new IllegalArgumentException("workId must be positive");
        }
        if (technicianId != null && technicianId <= 0) {
            throw new IllegalArgumentException("technicianId must be positive");
        }
    }
}
