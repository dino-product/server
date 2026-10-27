package com.orbit.organization;

/**
 * 작업 유형의 표시 정보.
 *
 * @param workTypeId 작업 유형 식별자
 * @param name 이름(삭제됐으면 삭제 직전 이름)
 * @param color 색상, 대문자 {@code #RRGGBB}(삭제됐으면 삭제 직전 색상)
 * @param deleted 발주사가 삭제한 유형인지
 */
public record WorkTypeLabel(Long workTypeId, String name, String color, boolean deleted) {}
