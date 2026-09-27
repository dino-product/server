package com.orbit.schedule.domain;

/** 작업 등록·기본정보 수정·배정 관리·취소를 하는 관리자. 요청한 조직의 소속(Membership)과 그 역할(총관리자·직원)이다. */
public record ManagerActor(MembershipId membershipId, OrganizationId organizationId, ActorRole role) implements Actor {

    public ManagerActor {
        if (membershipId == null) {
            throw new IllegalArgumentException("membershipId must not be null");
        }
        if (organizationId == null) {
            throw new IllegalArgumentException("organizationId must not be null");
        }
        if (role == null) {
            throw new IllegalArgumentException("role must not be null");
        }
    }
}
