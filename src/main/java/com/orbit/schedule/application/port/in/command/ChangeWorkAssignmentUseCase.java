package com.orbit.schedule.application.port.in.command;

import com.orbit.schedule.application.port.in.command.dto.ChangeWorkAssignmentCommand;
import com.orbit.schedule.application.port.in.command.dto.ScheduleChangeInfo;

/** 타임테이블에서 작업을 놓거나 옮긴 결과를 반영한다. 대기함 작업은 배정, 같은 기사는 일정 변경, 다른 기사는 재배정이다. */
public interface ChangeWorkAssignmentUseCase {

    ScheduleChangeInfo change(ChangeWorkAssignmentCommand command);
}
