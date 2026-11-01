package com.orbit.realtime.application.port.in.command;

import com.orbit.realtime.application.port.in.command.dto.OpenRealtimeStreamCommand;

public interface OpenRealtimeStreamUseCase {

    void open(OpenRealtimeStreamCommand command);
}
