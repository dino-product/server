package com.orbit.schedule.application.port.in.command;

import com.orbit.schedule.application.port.in.command.dto.CompletionReportRequestInfo;
import com.orbit.schedule.application.port.in.command.dto.RequestCompletionReportCommand;

public interface RequestCompletionReportUseCase {

    CompletionReportRequestInfo request(RequestCompletionReportCommand command);
}
