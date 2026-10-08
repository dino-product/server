package com.orbit.organization.domain;

/**
 * 기사 계약 상태. 명칭은 제안값이다(O-31). 활성 계약만 요청자가 되며, 비활성(총관리자의 비활성화·탈퇴)과 역할 변경 종료(직원 소속으로 바뀌어 끝난 행)는 행을
 * 남긴 채 상태만 바꾼다. 다시 활성이 되면 같은 계약 ID를 쓴다.
 */
public enum TechnicianStatus {
    ACTIVE,
    DEACTIVATED,
    ROLE_CHANGED
}
