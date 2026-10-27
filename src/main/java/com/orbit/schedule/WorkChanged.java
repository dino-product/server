package com.orbit.schedule;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 조직의 작업을 저장했다. 작업을 저장하는 모든 변경(등록·배정·재배정·일정 변경·배정 해제·기본정보 수정·수락·거절·시작·완료보고 요청·완료보고 제출·취소·대기함
 * 반환·작업 유형 해제)이 저장한 트랜잭션 안에서 발행한다. 무엇이 어떻게 바뀌었는지는 담지 않으므로 구독하는 쪽은 필요한 상태를 다시 조회한다. 대기함 반환·작업 유형
 * 해제처럼 여러 작업을 한 번에 바꾸는 변경은 조직마다 한 건에 모으고, 바뀐 작업이 없으면 발행하지 않는다. {@code workIds}의 순서에는 의미가 없다. 저장하지 않은
 * 요청(겹침 미확인 보류·중복 요청·거부)에는 발행하지 않는다.
 */
public record WorkChanged(Long organizationId, List<Long> workIds, Instant occurredAt) {

    public WorkChanged {
        workIds = List.copyOf(Objects.requireNonNull(workIds, "workIds must not be null"));
    }
}
