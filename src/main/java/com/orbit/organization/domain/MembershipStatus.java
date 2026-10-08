package com.orbit.organization.domain;

/**
 * 직원 소속 상태. 명칭은 제안값이다(O-31). 활성 소속만 요청자가 되며, 비활성(총관리자의 비활성화·탈퇴)과 역할 변경 종료(기사 계약으로 바뀌어 끝난 행)는 행을
 * 남긴 채 상태만 바꾼다.
 */
public enum MembershipStatus {
    ACTIVE,
    DEACTIVATED,
    ROLE_CHANGED
}
