package com.orbit.auth.application.port.in.command;

import com.orbit.auth.application.port.in.command.dto.LoginNonceInfo;

public interface IssueAppleLoginNonceUseCase {

    /** raw nonce를 발급한다. 앱은 그 SHA-256 hex를 Apple 로그인 요청의 nonce로 넣는다. */
    LoginNonceInfo issue();
}
