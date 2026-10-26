package com.orbit.organization;

import java.util.List;

/**
 * 작업 유형을 지울 때 그 유형을 쓰는 작업을 확인하고 시작 전 작업의 유형을 비우는 요구 인터페이스. 작업은 schedule이 소유하므로 organization이 선언하고 schedule이
 * 구현한다(의존 역전, ADR-001). 호출자의 트랜잭션 안에서 실행되므로 유형 해제는 유형 삭제와 함께 커밋되거나 함께 롤백된다. 인자는 null이 아닌 양수다.
 *
 * <p>시작 전 작업은 등록·배정됨·작업전, 시작한 작업은 작업중·완료·취소다([조직·계정] §7). 시작 전 작업은 유형 없음이 되고, 시작한 작업은 삭제된 유형을 계속
 * 참조해 삭제 직전 이름·색상을 보여 준다.
 */
public interface WorkTypeRelease {

    /** 그 발주사에서 이 작업 유형을 쓰는 작업을 시작 전·시작한 작업으로 나눠 센다. 상태를 바꾸지 않고 잠그지도 않는다. */
    WorkTypeDeletionImpact countImpact(Long organizationId, Long workTypeId);

    /**
     * 그 발주사에서 이 작업 유형을 쓰는 시작 전 작업의 유형을 비우고, 비운 작업을 돌려준다. 시작한 작업은 그대로 둔다. 다른 변경과 겹친 작업은 그 변경이 끝나기를
     * 기다렸다가 그때의 상태로 판단하므로 거부하지 않는다.
     *
     * <p>순서 전제: 같은 트랜잭션에서 그 유형 행을 삭제 표시로 바꿔 DB에 반영한 뒤 부른다. 작업에 유형을 지정하는 쪽은 {@link WorkTypeLookup#isSelectable}로
     * 유형 행을 공유 잠금으로 잡으므로, 그 행을 먼저 바꿔 두면 이 해제가 확인을 마친 작업의 커밋을 본 뒤에 실행되고 이후의 지정은 거부된다. 호출자의 트랜잭션은
     * READ COMMITTED여야 해제가 기다린 뒤 커밋된 작업을 본다.
     *
     * <p>실패: 호출자의 트랜잭션이 없으면 {@code IllegalTransactionStateException}을, READ COMMITTED보다 높은 격리 수준이면
     * {@link IllegalStateException}을 던진다. 후자는 호출자의 트랜잭션을 롤백 전용으로 만들므로 잡지 말고 전파한다(잡고 정상 반환하면 커밋 때
     * {@code UnexpectedRollbackException}으로 500이 된다).
     */
    List<WorkTypeReleasedWork> releaseForDeletion(Long organizationId, Long workTypeId);
}
