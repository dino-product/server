package com.orbit.schedule.domain;

/**
 * 작업을 조작하는 요청자. 요청한 조직에서의 소속(Membership)과 그 역할이며, 역할별 허용 행위는 작업 상태·배정 정책의 권한 표를 따른다. 활성 소속만
 * 행위자가 되도록 거르는 책임은 이 값을 만드는 쪽(LoadActorPort)에 있다.
 */
public record Actor(MembershipId membershipId, OrganizationId organizationId, ActorRole role) {

    public Actor {
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

    /** 작업 등록·기본정보 수정·배정 관리·취소는 총관리자와 직원만 할 수 있다. */
    public boolean canManageWorks() {
        return role == ActorRole.OWNER || role == ActorRole.STAFF;
    }
}
