package com.orbit.schedule.domain;

/**
 * 사용자가 한 회사의 기사로 등록된 관계(organization 쪽 회사별 기사)를 opaque ID로만 참조하는 ACL 값객체. 기사는 조직 소속(Membership)이 아니라 회사와
 * 계약한 별도 관계이므로 {@link MembershipId}와 섞이지 않게 타입을 나눈다. 작업의 담당기사와 기사별 조회·잠금이 이 타입을 쓴다.
 */
public record TechnicianId(Long value) {

    public TechnicianId {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("technicianId must be positive");
        }
    }
}
