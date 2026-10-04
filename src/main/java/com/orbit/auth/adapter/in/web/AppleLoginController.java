package com.orbit.auth.adapter.in.web;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.orbit.auth.adapter.in.web.docs.AppleLoginControllerDocs;
import com.orbit.auth.application.port.in.command.IssueAppleLoginNonceUseCase;

@RestController
@RequestMapping("/api/v1/auth/apple")
public class AppleLoginController implements AppleLoginControllerDocs {

    private final IssueAppleLoginNonceUseCase issueAppleLoginNonceUseCase;

    public AppleLoginController(IssueAppleLoginNonceUseCase issueAppleLoginNonceUseCase) {
        this.issueAppleLoginNonceUseCase = issueAppleLoginNonceUseCase;
    }

    @Override
    @PostMapping("/nonces")
    public AppleLoginNonceResponse issueNonce() {
        return AppleLoginNonceResponse.from(issueAppleLoginNonceUseCase.issue());
    }
}
