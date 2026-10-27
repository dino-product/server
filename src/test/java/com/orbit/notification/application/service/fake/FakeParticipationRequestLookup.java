package com.orbit.notification.application.service.fake;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.orbit.organization.ParticipationRequestLookup;

/** organization 참여 요청 조회 계약의 fake. 테스트가 넣은 요청의 거절 사유만 찾고, 넣지 않은 요청은 사유가 없다. */
public class FakeParticipationRequestLookup implements ParticipationRequestLookup {

    private final Map<Long, String> rejectReasons = new HashMap<>();

    public FakeParticipationRequestLookup rejectReason(Long requestId, String reason) {
        rejectReasons.put(requestId, reason);
        return this;
    }

    @Override
    public Optional<String> findRejectReason(Long requestId) {
        // 계약의 호출 전제: 인자는 null이 아닌 양수다.
        if (requestId == null || requestId <= 0) {
            throw new IllegalArgumentException("requestId must be positive");
        }
        return Optional.ofNullable(rejectReasons.get(requestId));
    }
}
