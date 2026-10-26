package com.orbit.profile;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("탈퇴 차단 사유")
class WithdrawalBlockTest {

    @Test
    @DisplayName("사유가 없거나 해결할 대상 수가 1보다 작으면 거부한다")
    void rejectsIncompleteBlock() {
        assertThatThrownBy(() -> new WithdrawalBlock(null, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("reason must not be null");
        assertThatThrownBy(() -> new WithdrawalBlock(WithdrawalBlockReason.SOLE_OWNER, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("count must be positive");
    }
}
