package com.orbit.organization;

/**
 * 기사 계약의 표시 정보.
 *
 * @param technicianId 기사 계약 식별자
 * @param accountId 기사 계정 식별자(이름은 호출자가 계정으로 찾는다)
 * @param technicianCategory 기사 유형. 없거나 삭제됐으면 null
 */
public record TechnicianDirectoryEntry(Long technicianId, Long accountId, MemberCategoryLabel technicianCategory) {}
