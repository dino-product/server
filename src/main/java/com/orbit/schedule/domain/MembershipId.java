package com.orbit.schedule.domain;

/**
 * organization 모듈의 조직 소속(Membership: 총관리자·직원)을 opaque ID로만 참조하는 ACL 값객체. 작업 등록자, 배정한 관리자, 배정을 끝낸 처리자가 이
 * 타입이다. 기사는 조직 소속이 아니므로 {@link TechnicianId}를 쓴다.
 */
public record MembershipId(Long value) {

    public MembershipId {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("membershipId must be positive");
        }
    }
}
