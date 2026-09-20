package com.orbit.schedule.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("작업 상태")
class WorkStatusTest {

    @ParameterizedTest
    @MethodSource("allowedTransitions")
    @DisplayName("화이트리스트에 정의된 상태로 전이한다")
    void transitionsToAllowedStatus(WorkStatus currentStatus, WorkStatus nextStatus) {
        assertThat(currentStatus.canTransitionTo(nextStatus)).isTrue();
        assertThat(currentStatus.transitionTo(nextStatus)).isEqualTo(nextStatus);
    }

    @ParameterizedTest
    @MethodSource("disallowedTransitions")
    @DisplayName("화이트리스트에 없는 상태 전이는 거부한다")
    void rejectsDisallowedTransition(WorkStatus currentStatus, WorkStatus nextStatus) {
        assertThat(currentStatus.canTransitionTo(nextStatus)).isFalse();
        assertThatThrownBy(() -> currentStatus.transitionTo(nextStatus))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cannot transition from %s to %s", currentStatus, nextStatus);
    }

    private static Stream<Arguments> allowedTransitions() {
        return Stream.of(
                Arguments.of(WorkStatus.REGISTERED, WorkStatus.PENDING_ACCEPTANCE),
                Arguments.of(WorkStatus.REGISTERED, WorkStatus.CANCELLED),
                Arguments.of(WorkStatus.PENDING_ACCEPTANCE, WorkStatus.ACCEPTED),
                Arguments.of(WorkStatus.PENDING_ACCEPTANCE, WorkStatus.REJECTED),
                Arguments.of(WorkStatus.PENDING_ACCEPTANCE, WorkStatus.CANCELLED),
                Arguments.of(WorkStatus.REJECTED, WorkStatus.PENDING_ACCEPTANCE),
                Arguments.of(WorkStatus.ACCEPTED, WorkStatus.IN_PROGRESS),
                Arguments.of(WorkStatus.ACCEPTED, WorkStatus.CANCELLED),
                Arguments.of(WorkStatus.IN_PROGRESS, WorkStatus.COMPLETED),
                Arguments.of(WorkStatus.IN_PROGRESS, WorkStatus.CANCELLED));
    }

    private static Stream<Arguments> disallowedTransitions() {
        return Stream.of(
                Arguments.of(WorkStatus.REGISTERED, WorkStatus.COMPLETED),
                Arguments.of(WorkStatus.COMPLETED, WorkStatus.IN_PROGRESS),
                Arguments.of(WorkStatus.CANCELLED, WorkStatus.PENDING_ACCEPTANCE),
                Arguments.of(WorkStatus.ACCEPTED, WorkStatus.REGISTERED),
                Arguments.of(WorkStatus.REJECTED, WorkStatus.ACCEPTED));
    }
}
