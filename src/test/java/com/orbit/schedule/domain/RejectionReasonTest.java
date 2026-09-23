package com.orbit.schedule.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("배정 거절 사유")
class RejectionReasonTest {

    @Test
    @DisplayName("거절 사유 5개가 정의된 순서대로 존재한다")
    void containsAllReasonsInOrder() {
        assertThat(RejectionReason.values())
                .containsExactly(
                        RejectionReason.SCHEDULE_CONFLICT,
                        RejectionReason.ALREADY_ASSIGNED,
                        RejectionReason.LOCATION_TOO_FAR,
                        RejectionReason.SCOPE_MISMATCH,
                        RejectionReason.OTHER);
    }
}
