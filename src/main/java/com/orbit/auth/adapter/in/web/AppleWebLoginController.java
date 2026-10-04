package com.orbit.auth.adapter.in.web;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.orbit.auth.adapter.in.web.docs.AppleWebLoginControllerDocs;
import com.orbit.auth.application.port.in.command.StartAppleWebLoginUseCase;
import com.orbit.auth.application.port.in.command.dto.AppleWebLoginStartInfo;
import com.orbit.auth.application.port.in.command.dto.StartAppleWebLoginCommand;
import com.orbit.shared.error.BusinessException;
import com.orbit.shared.error.CommonErrorCode;

/**
 * 웹·Android용 Apple 로그인. Apple은 콜백을 다른 사이트에서 보내는 form POST로 전달하므로, 시작한 브라우저를 확인하는 연결 쿠키는 {@code SameSite=None;
 * Secure; HttpOnly}로 콜백 경로에만 보낸다.
 */
@RestController
@RequestMapping("/api/v1/auth/apple")
public class AppleWebLoginController implements AppleWebLoginControllerDocs {

    static final String BROWSER_BINDING_COOKIE = "apple_login_binding";
    static final String COOKIE_PATH = "/api/v1/auth/apple";

    private final StartAppleWebLoginUseCase startAppleWebLoginUseCase;
    private final AppleWebReturnProperties returnProperties;

    public AppleWebLoginController(
            StartAppleWebLoginUseCase startAppleWebLoginUseCase, AppleWebReturnProperties returnProperties) {
        this.startAppleWebLoginUseCase = startAppleWebLoginUseCase;
        this.returnProperties = returnProperties;
    }

    @Override
    @GetMapping("/authorize")
    public ResponseEntity<Void> authorize(@RequestParam("client") String client) {
        String returnUri = returnProperties
                .returnUri(client)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.BAD_REQUEST));
        AppleWebLoginStartInfo started = startAppleWebLoginUseCase.start(new StartAppleWebLoginCommand(returnUri));
        ResponseCookie binding = ResponseCookie.from(BROWSER_BINDING_COOKIE, started.browserBinding())
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path(COOKIE_PATH)
                .maxAge(started.bindingLifetime())
                .build();
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(started.authorizationUri())
                .header(HttpHeaders.SET_COOKIE, binding.toString())
                .build();
    }
}
