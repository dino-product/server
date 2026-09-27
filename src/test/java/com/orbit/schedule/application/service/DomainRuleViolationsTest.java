package com.orbit.schedule.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.shared.error.BusinessException;

@DisplayName("도메인 규칙 위반 변환")
class DomainRuleViolationsTest {

    @Test
    @DisplayName("상태 규칙 위반은 작업 상태 오류로 바꾸고 원인을 보존한다")
    void translatesIllegalStateToInvalidWorkState() {
        IllegalStateException cause = new IllegalStateException("Cannot accept when status is REGISTERED");

        assertThatThrownBy(() -> DomainRuleViolations.call(() -> {
                    throw cause;
                }))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ScheduleErrorCode.INVALID_WORK_STATE))
                .hasCause(cause);
    }

    @Test
    @DisplayName("입력 규칙 위반은 작업 입력 오류로 바꾸고 원인을 보존한다")
    void translatesIllegalArgumentToInvalidWorkInput() {
        IllegalArgumentException cause = new IllegalArgumentException("name must not be blank");

        assertThatThrownBy(() -> DomainRuleViolations.call(() -> {
                    throw cause;
                }))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ScheduleErrorCode.INVALID_WORK_INPUT))
                .hasCause(cause);
    }

    @Test
    @DisplayName("반환값이 없는 도메인 호출의 상태 규칙 위반도 작업 상태 오류로 바꾼다")
    void translatesViolationFromVoidDomainCall() {
        Runnable violatingCall = () -> {
            throw new IllegalStateException("Cannot start when status is REGISTERED");
        };

        assertThatThrownBy(() -> DomainRuleViolations.run(violatingCall))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ScheduleErrorCode.INVALID_WORK_STATE));
    }

    @Test
    @DisplayName("규칙 위반이 없으면 결과를 그대로 돌려준다")
    void returnsResultWhenNoViolation() {
        assertThat(DomainRuleViolations.call(() -> "ok")).isEqualTo("ok");
    }

    @Test
    @DisplayName("프로그래밍 오류는 바꾸지 않고 그대로 전파한다")
    void doesNotTranslateProgrammingErrors() {
        NullPointerException bug = new NullPointerException("unexpected null");

        assertThatThrownBy(() -> DomainRuleViolations.call(() -> {
                    throw bug;
                }))
                .isSameAs(bug);
    }
}
