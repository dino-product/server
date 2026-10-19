package com.orbit.schedule.application.port.in.query.dto;

/**
 * 직원 소속(총관리자·직원)의 표시 정보. 작업의 등록자 표기에 쓴다.
 *
 * @param membershipId 소속 식별자
 * @param name 이름. 찾을 수 없으면 null이며 그때의 표기는 화면이 정한다
 * @param owner 총관리자인지. 찾을 수 없으면 null
 * @param staffType 직원유형. 유형이 없거나 찾을 수 없으면 null
 */
public record StaffInfo(Long membershipId, String name, Boolean owner, LabelInfo staffType) {}
