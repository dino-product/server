package com.orbit.auth.adapter.in.web;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.orbit.auth.adapter.in.web.docs.AppleLoginControllerDocs;
import com.orbit.auth.application.port.in.command.IssueAppleLoginNonceUseCase;
import com.orbit.auth.application.port.in.command.LoginWithAppleUseCase;
import com.orbit.auth.application.port.in.command.dto.LoginWithAppleCommand;

@RestController
@RequestMapping("/api/v1/auth/apple")
public class AppleLoginController implements AppleLoginControllerDocs {

    private final IssueAppleLoginNonceUseCase issueAppleLoginNonceUseCase;
    private final LoginWithAppleUseCase loginWithAppleUseCase;

    public AppleLoginController(
            IssueAppleLoginNonceUseCase issueAppleLoginNonceUseCase, LoginWithAppleUseCase loginWithAppleUseCase) {
        this.issueAppleLoginNonceUseCase = issueAppleLoginNonceUseCase;
        this.loginWithAppleUseCase = loginWithAppleUseCase;
    }

    @Override
    @PostMapping("/nonces")
    public AppleLoginNonceResponse issueNonce() {
        return AppleLoginNonceResponse.from(issueAppleLoginNonceUseCase.issue());
    }

    @Override
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody AppleLoginRequest request) {
        return LoginResponse.from(
                loginWithAppleUseCase.login(new LoginWithAppleCommand(request.idToken(), request.authorizationCode())));
    }
}
