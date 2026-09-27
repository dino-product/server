package com.orbit.schedule.domain;

/**
 * organization 모듈의 조직(발주사)을 opaque ID로만 참조하는 ACL 값객체. 작업이 속한 조직이며 조직 단위 격리(조회·변경 범위)의 기준이다. 권한은 그
 * 조직에서의 요청자 종류(총관리자·직원 소속 또는 기사 계약)로 판단한다.
 */
public record OrganizationId(Long value) {

    public OrganizationId {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("organizationId must be positive");
        }
    }
}
