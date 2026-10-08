package com.orbit.organization.domain;

/**
 * 직원 소속 상태. 명칭은 제안값이다(O-31). 활성 소속만 요청자가 된다. 총관리자의 비활성화·탈퇴와 기사로의 역할 변경은 모두 행을 남긴 채 비활성으로 바꾼다.
 */
public enum MembershipStatus {
    ACTIVE,
    DEACTIVATED
}
