package com.orbit.organization;

import java.time.Instant;

/**
 * 총관리자가 참여 요청을 거절했다. 거절과 같은 트랜잭션에서 발행되며, 소비 모듈은 커밋 뒤에 받는다.
 *
 * @param accountId 신청자 계정
 * @param reason 신청자에게 보여 줄 거절 사유. 사유 없이 거절했으면 {@code null}
 * @param occurredAt 거절 시각
 */
public record ParticipationRequestRejected(
        Long requestId, Long organizationId, Long accountId, String reason, Instant occurredAt) {}
