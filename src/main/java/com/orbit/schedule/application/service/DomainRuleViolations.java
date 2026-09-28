package com.orbit.schedule.application.service;

import java.util.function.Supplier;

import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.domain.SameTechnicianException;
import com.orbit.schedule.domain.UnchangedScheduleException;
import com.orbit.shared.error.BusinessException;

import lombok.extern.slf4j.Slf4j;

/**
 * Domain이 던지는 불변식 예외를 schedule 오류 코드로 바꾸는 Application 경계. Domain은 BusinessException에 의존하지 않으므로(errors.md) 서비스가 도메인
 * 호출을 이 경계로 감싼다. 상태 규칙 위반(IllegalStateException)은 409, 입력 규칙 위반(IllegalArgumentException)은 400이며 원인 예외는 debug 로그와
 * cause로 남긴다. 현재 값과 같은 재배정·일정 변경은 입력 오류와 구분해, 재시도·중복 요청을 호출자가 알아볼 수 있는 409 코드로 바꾼다.
 *
 * <p>사용 규칙: 람다에는 도메인 팩토리·메서드·값객체 생성자 호출만 넣는다. 포트·Clock·컬렉션 가공처럼 도메인 밖의 호출을 함께 넣으면, 그쪽에서 던진 같은 계열의
 * JDK·Spring 예외까지 400/409로 잘못 바뀐다. NullPointerException 등 프로그래밍 오류는 바꾸지 않고 그대로 전파해 500으로 드러낸다.
 */
@Slf4j
final class DomainRuleViolations {

    private DomainRuleViolations() {}

    static <T> T call(Supplier<T> domainCall) {
        try {
            return domainCall.get();
        } catch (IllegalStateException e) {
            log.debug("Domain state rule violated", e);
            throw new BusinessException(ScheduleErrorCode.INVALID_WORK_STATE, e);
        } catch (SameTechnicianException e) {
            log.debug("Reassignment to the current technician", e);
            throw new BusinessException(ScheduleErrorCode.SAME_TECHNICIAN, e);
        } catch (UnchangedScheduleException e) {
            log.debug("Reschedule to the current time", e);
            throw new BusinessException(ScheduleErrorCode.SCHEDULE_UNCHANGED, e);
        } catch (IllegalArgumentException e) {
            log.debug("Domain input rule violated", e);
            throw new BusinessException(ScheduleErrorCode.INVALID_WORK_INPUT, e);
        }
    }

    static void run(Runnable domainCall) {
        call(() -> {
            domainCall.run();
            return null;
        });
    }
}
