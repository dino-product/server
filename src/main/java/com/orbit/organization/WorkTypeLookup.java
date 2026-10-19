package com.orbit.organization;

import java.util.List;
import java.util.Set;

/**
 * 작업 유형 공개 조회. 작업은 작업 유형을 식별자로만 참조하므로, 작업에 유형을 지정할 때와 작업을 보여 줄 때 다른 모듈이 이 조회를 쓴다. 인자는 null이 아니고
 * 식별자는 양수이며 어기면 {@link IllegalArgumentException}이다.
 */
public interface WorkTypeLookup {

    /**
     * 이 작업 유형이 이 발주사에서 지금 고를 수 있는(삭제되지 않은) 유형인지 확인한다. 다른 발주사의 유형, 삭제된 유형, 없는 유형이면 {@code false}다.
     *
     * <p>{@code true}이면 그 유형 행을 호출 트랜잭션이 끝날 때까지 공유 잠금으로 잡는다. 같은 유형의 삭제는 이 잠금이 풀리기를 기다린 뒤 시작 전 작업의 유형을
     * 해제하므로, 확인과 작업 저장 사이에 유형이 지워져도 삭제가 그 작업을 놓치지 않는다. 반대로 삭제가 먼저 진행 중이면 이 확인이 삭제의 커밋을 기다렸다가
     * {@code false}를 돌려준다. 그래서 확인한 작업 저장과 같은 트랜잭션에서 부르고, 그 트랜잭션은 읽기 전용이 아니어야 한다(PostgreSQL은 읽기 전용
     * 트랜잭션의 행 잠금을 거부한다).
     */
    boolean isSelectable(Long organizationId, Long workTypeId);

    /**
     * 지정한 작업 유형의 표시 정보를 돌려준다. 이미 작업에 붙은 유형을 보여 주는 데 쓰므로 삭제된 유형도 삭제 직전 이름·색상과 삭제 표시를 담는다. 다른 발주사의
     * 유형이거나 없는 유형은 결과에서 빠지며 순서는 보장하지 않는다.
     */
    List<WorkTypeLabel> findLabels(Long organizationId, Set<Long> workTypeIds);
}
