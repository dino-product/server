package com.orbit.auth.adapter.in.web;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.orbit.auth.adapter.in.web.docs.KakaoLoginControllerDocs;
import com.orbit.auth.application.port.in.command.IssueLoginNonceUseCase;

@RestController
@RequestMapping("/api/v1/auth/kakao")
public class KakaoLoginController implements KakaoLoginControllerDocs {

    private final IssueLoginNonceUseCase issueLoginNonceUseCase;

    public KakaoLoginController(IssueLoginNonceUseCase issueLoginNonceUseCase) {
        this.issueLoginNonceUseCase = issueLoginNonceUseCase;
    }

    @Override
    @PostMapping("/nonces")
    public LoginNonceResponse issueNonce() {
        return LoginNonceResponse.from(issueLoginNonceUseCase.issue());
    }
}
