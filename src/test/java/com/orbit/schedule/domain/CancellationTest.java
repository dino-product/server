package com.orbit.schedule.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("취소 기록")
class CancellationTest {

    private static final Instant CANCELLED_AT = Instant.parse("2026-09-20T02:00:00Z");
    private static final MembershipId MANAGER_ID = new MembershipId(99L);

    @Test
    @DisplayName("취소 시각·처리자·사유를 담고 시각은 마이크로초로 자른다")
    void holdsCancellation() {
        Cancellation cancellation =
                new Cancellation(CANCELLED_AT.plusNanos(1_999), MANAGER_ID, CancellationReason.CUSTOMER_REQUEST, null);

        assertThat(cancellation.cancelledAt()).isEqualTo(CANCELLED_AT.plusNanos(1_000));
        assertThat(cancellation.cancelledBy()).isEqualTo(MANAGER_ID);
        assertThat(cancellation.reason()).isEqualTo(CancellationReason.CUSTOMER_REQUEST);
        assertThat(cancellation.note()).isNull();
    }

    @Test
    @DisplayName("취소 시각·처리자·사유가 없으면 거부한다")
    void requiresTimeActorAndReason() {
        assertThatThrownBy(() -> new Cancellation(null, MANAGER_ID, CancellationReason.CUSTOMER_REQUEST, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("cancelledAt must not be null");
        assertThatThrownBy(() -> new Cancellation(CANCELLED_AT, null, CancellationReason.CUSTOMER_REQUEST, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("cancelledBy must not be null");
        assertThatThrownBy(() -> new Cancellation(CANCELLED_AT, MANAGER_ID, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("cancellation reason must not be null");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    @DisplayName("기타 사유인데 메모가 비어 있으면 거부한다")
    void requiresNoteForOther(String note) {
        assertThatThrownBy(() -> new Cancellation(CANCELLED_AT, MANAGER_ID, CancellationReason.OTHER, note))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("cancellation note is required for OTHER");
    }

    @Test
    @DisplayName("기타가 아닌 사유는 메모를 받지 않고, 빈 메모는 미입력으로 본다")
    void acceptsNoteOnlyForOther() {
        assertThatThrownBy(() -> new Cancellation(CANCELLED_AT, MANAGER_ID, CancellationReason.TECHNICIAN_ISSUE, "사정"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("cancellation note is only for OTHER");
        assertThat(new Cancellation(CANCELLED_AT, MANAGER_ID, CancellationReason.TECHNICIAN_ISSUE, " ").note())
                .isNull();
    }

    @Test
    @DisplayName("기타 메모는 255자까지 받고 넘으면 거부한다")
    void limitsNoteLength() {
        assertThat(new Cancellation(CANCELLED_AT, MANAGER_ID, CancellationReason.OTHER, "가".repeat(255)).note())
                .hasSize(255);
        assertThatThrownBy(() -> new Cancellation(CANCELLED_AT, MANAGER_ID, CancellationReason.OTHER, "가".repeat(256)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("cancellation note must be at most 255 characters");
    }
}
