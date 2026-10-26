package com.orbit.organization;

/**
 * 작업 유형을 지우면 작업이 받는 영향의 건수.
 *
 * @param releasedCount 유형 없음이 될 시작 전 작업(등록·배정됨·작업전) 수
 * @param keptCount 삭제된 유형을 그대로 유지할 시작한 작업(작업중·완료·취소) 수
 */
public record WorkTypeDeletionImpact(long releasedCount, long keptCount) {

    public WorkTypeDeletionImpact {
        if (releasedCount < 0 || keptCount < 0) {
            throw new IllegalArgumentException("counts must not be negative");
        }
    }
}
