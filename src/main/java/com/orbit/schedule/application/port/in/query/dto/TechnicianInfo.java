package com.orbit.schedule.application.port.in.query.dto;

/**
 * 기사 계약의 표시 정보.
 *
 * @param technicianId 기사 계약 식별자
 * @param name 기사 이름. 찾을 수 없으면 null이며 그때의 표기는 화면이 정한다
 * @param technicianType 기사유형. 유형이 없거나 찾을 수 없으면 null
 */
public record TechnicianInfo(Long technicianId, String name, LabelInfo technicianType) {}
