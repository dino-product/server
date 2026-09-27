package com.orbit.schedule.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("작업 상태")
class WorkStatusTest {

    @Test
    @DisplayName("거절은 상태가 아니라 배정 이력의 결과라 진행 상태에 없다")
    void doesNotDefineRejectedStatus() {
        assertThat(WorkStatus.values())
                .containsExactly(
                        WorkStatus.REGISTERED,
                        WorkStatus.PENDING_ACCEPTANCE,
                        WorkStatus.ACCEPTED,
                        WorkStatus.IN_PROGRESS,
                        WorkStatus.COMPLETED,
                        WorkStatus.CANCELLED);
    }

    @ParameterizedTest
    @EnumSource(WorkStatus.class)
    @DisplayName("상태가 그대로인 변경은 전이가 아니므로 자기 자신으로의 전이는 허용하지 않는다")
    void rejectsSelfTransition(WorkStatus status) {
        assertThat(status.canTransitionTo(status)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(WorkStatus.class)
    @DisplayName("어떤 상태로도 전이할 수 없는 완료·취소만 종료 상태다")
    void terminalStatusesAreThoseWithoutAnyTransition(WorkStatus status) {
        boolean hasNoTransition = Arrays.stream(WorkStatus.values()).noneMatch(status::canTransitionTo);

        assertThat(status.isTerminal()).isEqualTo(hasNoTransition);
    }

    @ParameterizedTest
    @EnumSource(
            value = WorkStatus.class,
            names = {"PENDING_ACCEPTANCE", "ACCEPTED", "IN_PROGRESS"})
    @DisplayName("수락대기·수락됨·작업중은 일정을 점유하는 활성 상태다")
    void activeStatusesOccupySchedule(WorkStatus status) {
        assertThat(status.isActive()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(
            value = WorkStatus.class,
            mode = EnumSource.Mode.EXCLUDE,
            names = {"PENDING_ACCEPTANCE", "ACCEPTED", "IN_PROGRESS"})
    @DisplayName("그 밖의 상태(등록·완료·취소)는 일정을 점유하지 않는다")
    void inactiveStatusesDoNotOccupySchedule(WorkStatus status) {
        assertThat(status.isActive()).isFalse();
    }

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
                // 배정
                Arguments.of(WorkStatus.REGISTERED, WorkStatus.PENDING_ACCEPTANCE),
                // 수락
                Arguments.of(WorkStatus.PENDING_ACCEPTANCE, WorkStatus.ACCEPTED),
                // 거절·배정 해제로 대기함 복귀
                Arguments.of(WorkStatus.PENDING_ACCEPTANCE, WorkStatus.REGISTERED),
                Arguments.of(WorkStatus.ACCEPTED, WorkStatus.REGISTERED),
                // 수락 이후 재배정·일정 변경은 재수락
                Arguments.of(WorkStatus.ACCEPTED, WorkStatus.PENDING_ACCEPTANCE),
                // 시작·완료보고
                Arguments.of(WorkStatus.ACCEPTED, WorkStatus.IN_PROGRESS),
                Arguments.of(WorkStatus.IN_PROGRESS, WorkStatus.COMPLETED),
                // 취소는 완료 전까지
                Arguments.of(WorkStatus.REGISTERED, WorkStatus.CANCELLED),
                Arguments.of(WorkStatus.PENDING_ACCEPTANCE, WorkStatus.CANCELLED),
                Arguments.of(WorkStatus.ACCEPTED, WorkStatus.CANCELLED),
                Arguments.of(WorkStatus.IN_PROGRESS, WorkStatus.CANCELLED));
    }

    private static Stream<Arguments> disallowedTransitions() {
        return Stream.of(
                Arguments.of(WorkStatus.REGISTERED, WorkStatus.ACCEPTED),
                Arguments.of(WorkStatus.REGISTERED, WorkStatus.IN_PROGRESS),
                Arguments.of(WorkStatus.REGISTERED, WorkStatus.COMPLETED),
                Arguments.of(WorkStatus.PENDING_ACCEPTANCE, WorkStatus.IN_PROGRESS),
                Arguments.of(WorkStatus.PENDING_ACCEPTANCE, WorkStatus.COMPLETED),
                Arguments.of(WorkStatus.ACCEPTED, WorkStatus.COMPLETED),
                Arguments.of(WorkStatus.IN_PROGRESS, WorkStatus.REGISTERED),
                Arguments.of(WorkStatus.IN_PROGRESS, WorkStatus.PENDING_ACCEPTANCE),
                Arguments.of(WorkStatus.IN_PROGRESS, WorkStatus.ACCEPTED),
                Arguments.of(WorkStatus.COMPLETED, WorkStatus.IN_PROGRESS),
                Arguments.of(WorkStatus.COMPLETED, WorkStatus.CANCELLED),
                Arguments.of(WorkStatus.CANCELLED, WorkStatus.REGISTERED),
                Arguments.of(WorkStatus.CANCELLED, WorkStatus.PENDING_ACCEPTANCE));
    }
}
