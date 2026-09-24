package com.orbit.schedule.application.port.in.command;

import com.orbit.schedule.application.port.in.command.dto.SubmitCompletionReportCommand;

public interface SubmitCompletionReportUseCase {

    void submit(SubmitCompletionReportCommand command);
}
