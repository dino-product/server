package com.orbit.schedule.domain;

/** organization 모듈의 Membership을 opaque ID로만 참조하는 ACL 값객체. 담당기사·등록자 두 역할 모두 이 타입을 공용으로 쓴다. */
public record MembershipId(Long value) {

    public MembershipId {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("membershipId must be positive");
        }
    }
}
