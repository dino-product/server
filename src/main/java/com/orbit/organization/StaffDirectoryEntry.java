package com.orbit.organization;

/**
 * 직원 소속의 표시 정보.
 *
 * @param membershipId 직원 소속 식별자
 * @param accountId 소속 계정 식별자(이름은 호출자가 계정으로 찾는다)
 * @param owner 총관리자인지
 * @param staffCategory 직원 유형. 없거나 삭제됐으면 null
 */
public record StaffDirectoryEntry(
        Long membershipId, Long accountId, boolean owner, MemberCategoryLabel staffCategory) {}
