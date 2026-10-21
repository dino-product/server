package com.orbit.organization;

/**
 * 기사 계약이 역할 변경·비활성화로 끝날 때 그 기사의 작업을 대기함으로 돌리는 요구 인터페이스. organization이 선언하고 schedule이 구현한다(의존 역전,
 * ADR-001). 호출자의 트랜잭션 안에서 실행되므로 반환은 역할 변경·비활성화와 함께 커밋되거나 함께 롤백된다. 반환은 배정됨·작업전 작업의 담당기사·시간을 비우고
 * 대기함으로 돌리는 일이며, 처리자는 그 배정의 종료 처리자로 남는다. 이때 그 기사를 미리 입력한 담당기사로 둔 대기함 작업에서는 담당기사만 비우며, 이 작업은
 * 반환한 작업 목록에 넣지 않는다. 인자는 null이 아닌 양수다.
 *
 * <p>실패: 같은 기사의 앞선 일정 변경이나 기사 요청(수락·거절·시작)을 기다리다 대기 한도를 넘기면 {@link TechnicianWorkReleaseBusyException}을
 * 던진다. 호출자의 트랜잭션은 이미 롤백 전용이 되므로 잡고 정상 반환하지 말고(커밋 때 {@code UnexpectedRollbackException}으로 500이 된다) 자기
 * 오류로 바꿔 다시 던진다. 호출자의 트랜잭션이 READ COMMITTED보다 높은 격리 수준이면 {@link IllegalStateException}을 던진다. 반환 중 작업 상태 규칙이 깨지면 그
 * 밖의 예외(500)를 던지며, 이것도 잡지 말고 전파한다.
 *
 * <p>순서 전제: 구현은 기사 일정 잠금을 잡은 뒤 그 기사의 작업을 읽는다. 호출 전 같은 트랜잭션에서 그 작업을 읽어 두지 않는다. 같은 기사를 다루는
 * organization 흐름은 발주사 행 잠금 같은 명시적 잠금을 이 호출 전에 잡아 한 줄로 서고, 호출 뒤에는 새 명시적 잠금을 잡지 않는다(배정 쪽은 기사 일정 잠금
 * 안에서 기사 계약을 읽기만 하므로, 커밋 때 반영되는 소속 행 변경은 교착을 만들지 않는다).
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
