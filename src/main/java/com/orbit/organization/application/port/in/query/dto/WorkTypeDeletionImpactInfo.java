package com.orbit.organization.application.port.in.query.dto;

/**
 * 작업 유형 삭제 확인 창에 보일 영향 건수.
 *
 * @param releasedCount 유형 없음이 될 등록·배정됨·작업전 작업 수
 * @param keptCount 삭제된 유형을 그대로 유지할 작업중·완료·취소 작업 수
 */
public record WorkTypeDeletionImpactInfo(long releasedCount, long keptCount) {}
