package com.orbit.organization;

/**
 * 직원 유형·기사 유형의 표시 정보. 직원 유형과 기사 유형은 식별자를 따로 매긴다.
 *
 * @param categoryId 유형 식별자
 * @param name 이름
 * @param color 색상, 대문자 {@code #RRGGBB}
 */
public record MemberCategoryLabel(Long categoryId, String name, String color) {}
