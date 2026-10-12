package com.orbit.organization.domain;

/**
 * 직원 소속 상태. 명칭은 제안값이다(O-31). 비활성은 총관리자 지정 거부(O-13)를 위해 먼저 두었고, 비활성화·재활성화 전이는 HM-292에서 추가한다. 역할 변경
 * 종료는 해당 유즈케이스와 함께 추가한다.
 */
public enum MembershipStatus {
    ACTIVE,
    DEACTIVATED
}
