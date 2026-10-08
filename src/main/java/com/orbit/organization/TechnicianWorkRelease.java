package com.orbit.organization;

/**
 * 기사 계약이 역할 변경·비활성화로 끝날 때 그 기사의 작업을 대기함으로 돌리는 요구 인터페이스. organization이 선언하고 schedule이 구현한다(의존 역전,
 * ADR-001). 호출자의 트랜잭션 안에서 실행되므로 반환은 역할 변경·비활성화와 함께 커밋되거나 함께 롤백된다. 반환은 배정됨·작업전 작업의 담당기사·시간을 비우고
 * 대기함으로 돌리는 일이며, 처리자는 그 배정의 종료 처리자로 남는다. 인자는 null이 아닌 양수다.
 */
public interface TechnicianWorkRelease {

    /**
     * 역할 변경([조직·계정] §4.1): 작업중 작업이 있으면 아무것도 바꾸지 않고 {@link WorkReleaseBlocked}로 그 작업을, 없으면 배정됨·작업전 작업을 대기함으로 돌리고
     * {@link WorkReleased}로 돌린 작업을 돌려준다.
     */
    TechnicianWorkReleaseResult releaseForRoleChange(
            Long organizationId, Long technicianId, Long processedByMembershipId);

    /** 비활성화([스케줄·배정] §9): 배정됨·작업전 작업을 대기함으로 돌리고 작업중·완료 작업은 그대로 둔다. 거부하지 않는다. */
    void releaseForDeactivation(Long organizationId, Long technicianId, Long processedByMembershipId);
}
