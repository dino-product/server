package com.orbit.organization;

import java.time.Instant;

/**
 * 총관리자가 참여 요청을 거절했다. 거절과 같은 트랜잭션에서 발행되며, 소비 모듈은 커밋 뒤에 받는다. 총관리자가 직접 입력한 거절 사유는 이벤트 보관 테이블에 기한 없이
 * 남지 않도록 싣지 않으며, 필요한 소비 모듈은 {@link ParticipationRequestLookup#findRejectReason}으로 조회한다.
 *
 * @param accountId 신청자 계정
 * @param occurredAt 거절 시각
 */
public record ParticipationRequestRejected(Long requestId, Long organizationId, Long accountId, Instant occurredAt) {}
