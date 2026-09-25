package com.orbit.schedule.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("배정 거절 사유")
class RejectionTest {

    @Test
    @DisplayName("사유가 없으면 거부한다")
    void requiresReason() {
        assertThatThrownBy(() -> new Rejection(null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("rejectionReason must not be null");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    @DisplayName("기타 사유는 메모가 없으면 거부한다")
    void requiresNoteForOther(String note) {
        assertThatThrownBy(() -> new Rejection(RejectionReason.OTHER, note))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("rejection note is required for OTHER");
    }

    @Test
    @DisplayName("기타 사유의 메모는 255자까지 받는다")
    void limitsNoteLength() {
        assertThat(new Rejection(RejectionReason.OTHER, "가".repeat(255)).note()).hasSize(255);
        assertThatThrownBy(() -> new Rejection(RejectionReason.OTHER, "가".repeat(256)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("rejection note must be at most 255 characters");
    }

    @ParameterizedTest
    @EnumSource(value = RejectionReason.class, mode = EnumSource.Mode.EXCLUDE, names = "OTHER")
    @DisplayName("기타가 아닌 사유는 메모를 받지 않고, 빈 메모는 미입력으로 본다")
    void acceptsNoteOnlyForOther(RejectionReason reason) {
        assertThatThrownBy(() -> new Rejection(reason, "메모"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("rejection note is only for OTHER");
        assertThat(new Rejection(reason, " ").note()).isNull();
    }
}
