package com.orbit.schedule.application.port.in.query.dto;

/**
 * 조직이 정한 분류(작업유형·기사유형·직원유형)의 표시 정보.
 *
 * @param id 유형 식별자
 * @param name 유형 이름. 찾을 수 없으면 null
 * @param color 유형 색. 없거나 찾을 수 없으면 null
 */
public record LabelInfo(Long id, String name, String color) {}
