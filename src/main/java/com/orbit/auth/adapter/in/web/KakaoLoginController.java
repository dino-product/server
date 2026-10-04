package com.orbit.auth.adapter.in.web;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.orbit.auth.adapter.in.web.docs.KakaoLoginControllerDocs;
import com.orbit.auth.application.port.in.command.IssueLoginNonceUseCase;
import com.orbit.auth.application.port.in.command.LoginWithKakaoUseCase;
import com.orbit.auth.application.port.in.command.dto.LoginWithKakaoCommand;

@RestController
@RequestMapping("/api/v1/auth/kakao")
public class KakaoLoginController implements KakaoLoginControllerDocs {

    private final IssueLoginNonceUseCase issueLoginNonceUseCase;
    private final LoginWithKakaoUseCase loginWithKakaoUseCase;

    public KakaoLoginController(
            IssueLoginNonceUseCase issueLoginNonceUseCase, LoginWithKakaoUseCase loginWithKakaoUseCase) {
        this.issueLoginNonceUseCase = issueLoginNonceUseCase;
        this.loginWithKakaoUseCase = loginWithKakaoUseCase;
    }

    @Override
    @PostMapping("/nonces")
    public LoginNonceResponse issueNonce() {
        return LoginNonceResponse.from(issueLoginNonceUseCase.issue());
    }

    @Override
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody KakaoLoginRequest request) {
        return LoginResponse.from(loginWithKakaoUseCase.login(new LoginWithKakaoCommand(request.idToken())));
    }
}
