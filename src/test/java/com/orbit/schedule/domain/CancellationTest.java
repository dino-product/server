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
        Cancellation cancellation = new Cancellation(CANCELLED_AT.plusNanos(1_999), MANAGER_ID, "고객 요청");

        assertThat(cancellation.cancelledAt()).isEqualTo(CANCELLED_AT.plusNanos(1_000));
        assertThat(cancellation.cancelledBy()).isEqualTo(MANAGER_ID);
        assertThat(cancellation.reason()).isEqualTo("고객 요청");
    }

    @Test
    @DisplayName("취소 시각·처리자가 없으면 거부한다")
    void requiresTimeAndActor() {
        assertThatThrownBy(() -> new Cancellation(null, MANAGER_ID, "고객 요청"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("cancelledAt must not be null");
        assertThatThrownBy(() -> new Cancellation(CANCELLED_AT, null, "고객 요청"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("cancelledBy must not be null");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    @DisplayName("사유가 비어 있으면 거부한다")
    void rejectsBlankReason(String reason) {
        assertThatThrownBy(() -> new Cancellation(CANCELLED_AT, MANAGER_ID, reason))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("reason must not be blank");
    }

    @Test
    @DisplayName("사유는 255자까지 받고 넘으면 거부한다")
    void limitsReasonLength() {
        assertThat(new Cancellation(CANCELLED_AT, MANAGER_ID, "가".repeat(255)).reason())
                .hasSize(255);
        assertThatThrownBy(() -> new Cancellation(CANCELLED_AT, MANAGER_ID, "가".repeat(256)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("reason must be at most 255 characters");
    }
}
