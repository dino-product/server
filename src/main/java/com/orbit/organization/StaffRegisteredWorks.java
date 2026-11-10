package com.orbit.organization;

/**
 * 직원 소속이 등록한 작업 중 끝나지 않은 것이 있는지 확인하는 요구 인터페이스. organization이 선언하고 schedule이 구현한다(의존 역전, ADR-001). 직원을 기사로
 * 바꾸기 전에 호출하며, 있으면 역할 변경을 거부한다(2026-10-21 결정, [조직·계정] 정책에 없음). 끝나지 않은 작업은 대기함·배정됨·작업전·작업중 작업이며 완료·취소
 * 작업은 보지 않는다. 인자는 null이 아닌 양수다.
 *
 * <p>호출자는 발주사 행을 잠근 트랜잭션 안에서 부른다. 구현은 잠금을 잡지 않고 커밋된 상태를 읽으므로, 확인과 같은 직원의 새 작업 등록이 동시에 커밋되면 그
 * 작업은 확인을 통과할 수 있다(작업 등록은 발주사 행을 잠그지 않는다).
 */
public interface StaffRegisteredWorks {

    /** 그 발주사에서 이 직원 소속이 등록자인 끝나지 않은 작업이 하나라도 있는지. */
    boolean existsUnfinished(Long organizationId, Long membershipId);
}
