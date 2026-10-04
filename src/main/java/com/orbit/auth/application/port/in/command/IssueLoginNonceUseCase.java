package com.orbit.auth.application.port.in.command;

import com.orbit.auth.application.port.in.command.dto.LoginNonceInfo;

public interface IssueLoginNonceUseCase {

    LoginNonceInfo issue();
}
