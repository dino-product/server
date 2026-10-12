package com.orbit.organization.domain;

/**
 * 기사 계약 상태. 명칭은 제안값이다(O-31). 활성 계약만 요청자가 된다. 총관리자의 비활성화·탈퇴와 직원으로의 역할 변경은 모두 행을 남긴 채 비활성으로 바꾸고,
 * 다시 활성이 되면 같은 계약 ID를 쓴다.
 */
public enum TechnicianStatus {
    ACTIVE,
    DEACTIVATED
}
