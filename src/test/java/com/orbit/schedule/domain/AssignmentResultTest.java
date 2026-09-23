package com.orbit.schedule.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("배정 시도 결과")
class AssignmentResultTest {

    @Test
    @DisplayName("배정 결과 4개가 정의된 순서대로 존재한다")
    void containsAllResultsInOrder() {
        assertThat(AssignmentResult.values())
                .containsExactly(
                        AssignmentResult.PENDING,
                        AssignmentResult.ACCEPTED,
                        AssignmentResult.REJECTED,
                        AssignmentResult.REASSIGNED);
    }
}
