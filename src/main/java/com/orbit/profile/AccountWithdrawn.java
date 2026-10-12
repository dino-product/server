package com.orbit.profile;

import java.time.Instant;

/**
 * 계정이 탈퇴했다는 사실. 탈퇴 트랜잭션 안에서 발행하고 구독자는 커밋 뒤 다른 트랜잭션에서 받는다. 탈퇴로 상태가 바뀌어야 하는 모듈이 구독한다 — organization은
 * 모든 소속 비활성화, schedule은 기사 소속의 작업전 작업 대기함 반환([조직·계정] 정책 §5.1). 개인정보를 퍼뜨리지 않도록 계정 식별자와 발생 시각만 담는다.
 */
public record AccountWithdrawn(Long accountId, Instant occurredAt) {}
