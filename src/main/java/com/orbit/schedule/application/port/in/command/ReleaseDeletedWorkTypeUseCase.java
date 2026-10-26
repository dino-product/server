package com.orbit.schedule.application.port.in.command;

import java.util.List;

import com.orbit.schedule.application.port.in.command.dto.ReleaseDeletedWorkTypeCommand;
import com.orbit.schedule.application.port.in.command.dto.ReleasedWorkTypeInfo;

public interface ReleaseDeletedWorkTypeUseCase {

    List<ReleasedWorkTypeInfo> release(ReleaseDeletedWorkTypeCommand command);
}
