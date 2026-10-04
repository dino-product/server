package com.orbit.auth.adapter.in.web;

import java.net.URI;
import java.time.Duration;

import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import com.orbit.auth.adapter.in.web.docs.AppleWebLoginControllerDocs;
import com.orbit.auth.application.port.in.command.CompleteAppleWebLoginUseCase;
import com.orbit.auth.application.port.in.command.ExchangeAppleWebLoginUseCase;
import com.orbit.auth.application.port.in.command.StartAppleWebLoginUseCase;
import com.orbit.auth.application.port.in.command.dto.AppleWebLoginCompletion;
import com.orbit.auth.application.port.in.command.dto.AppleWebLoginStartInfo;
import com.orbit.auth.application.port.in.command.dto.CompleteAppleWebLoginCommand;
import com.orbit.auth.application.port.in.command.dto.ExchangeAppleWebLoginCommand;
import com.orbit.auth.application.port.in.command.dto.StartAppleWebLoginCommand;
import com.orbit.shared.error.BusinessException;
import com.orbit.shared.error.CommonErrorCode;

/**
 * 웹·Android용 Apple 로그인. Apple은 콜백을 다른 사이트에서 보내는 form POST로 전달하므로, 시작한 브라우저를 확인하는 연결 쿠키는 {@code SameSite=None;
 * Secure; HttpOnly}로 Apple 로그인 경로에만 보낸다. 콜백은 Access Token 대신 일회성 교환 코드만 복귀 주소에 실어 토큰이 URL·브라우저 기록에 남지 않게 한다.
 */
@RestController
@RequestMapping("/api/v1/auth/apple")
public class AppleWebLoginController implements AppleWebLoginControllerDocs {

    static final String BROWSER_BINDING_COOKIE = "apple_login_binding";
    static final String COOKIE_PATH = "/api/v1/auth/apple";

    private final StartAppleWebLoginUseCase startAppleWebLoginUseCase;
    private final CompleteAppleWebLoginUseCase completeAppleWebLoginUseCase;
    private final ExchangeAppleWebLoginUseCase exchangeAppleWebLoginUseCase;
    private final AppleWebReturnProperties returnProperties;

    public AppleWebLoginController(
            StartAppleWebLoginUseCase startAppleWebLoginUseCase,
            CompleteAppleWebLoginUseCase completeAppleWebLoginUseCase,
            ExchangeAppleWebLoginUseCase exchangeAppleWebLoginUseCase,
            AppleWebReturnProperties returnProperties) {
        this.startAppleWebLoginUseCase = startAppleWebLoginUseCase;
        this.completeAppleWebLoginUseCase = completeAppleWebLoginUseCase;
        this.exchangeAppleWebLoginUseCase = exchangeAppleWebLoginUseCase;
        this.returnProperties = returnProperties;
    }

    @Override
    @GetMapping("/authorize")
    public ResponseEntity<Void> authorize(@RequestParam("client") String client) {
        String returnUri = returnProperties
                .returnUri(client)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.BAD_REQUEST));
        AppleWebLoginStartInfo started = startAppleWebLoginUseCase.start(new StartAppleWebLoginCommand(returnUri));
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(started.authorizationUri())
                .header(
                        HttpHeaders.SET_COOKIE,
                        bindingCookie(started.browserBinding(), started.bindingLifetime())
                                .toString())
                .build();
    }

    @Override
    @PostMapping(path = "/callback", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> callback(
            @RequestParam(name = "state", required = false) String state,
            @RequestParam(name = "code", required = false) String code,
            @RequestParam(name = "id_token", required = false) String idToken,
            @RequestParam(name = "error", required = false) String error,
            @CookieValue(name = BROWSER_BINDING_COOKIE, required = false) String browserBinding) {
        AppleWebLoginCompletion completion = completeAppleWebLoginUseCase.complete(
                new CompleteAppleWebLoginCommand(state, browserBinding, idToken, code, error));
        URI location = completion.exchangeCode() != null
                ? withQuery(completion.returnUri(), "code", completion.exchangeCode())
                : withQuery(completion.returnUri(), "error", completion.errorCode());
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(location)
                .header(HttpHeaders.SET_COOKIE, bindingCookie("", Duration.ZERO).toString())
                .build();
    }

    @Override
    @PostMapping("/exchange")
    public LoginResponse exchange(@Valid @RequestBody AppleLoginExchangeRequest request) {
        return LoginResponse.from(
                exchangeAppleWebLoginUseCase.exchange(new ExchangeAppleWebLoginCommand(request.code())));
    }

    private static ResponseCookie bindingCookie(String value, Duration maxAge) {
        return ResponseCookie.from(BROWSER_BINDING_COOKIE, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path(COOKIE_PATH)
                .maxAge(maxAge)
                .build();
    }

    private static URI withQuery(String uri, String name, String value) {
        return UriComponentsBuilder.fromUriString(uri)
                .queryParam(name, value)
                .encode()
                .build()
                .toUri();
    }
}
