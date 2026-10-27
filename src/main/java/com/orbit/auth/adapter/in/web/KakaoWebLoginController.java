package com.orbit.auth.adapter.in.web;

import java.time.Duration;

import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.orbit.auth.adapter.in.web.docs.KakaoWebLoginControllerDocs;
import com.orbit.auth.application.port.in.command.CompleteKakaoWebLoginUseCase;
import com.orbit.auth.application.port.in.command.ExchangeWebLoginUseCase;
import com.orbit.auth.application.port.in.command.StartKakaoWebLoginUseCase;
import com.orbit.auth.application.port.in.command.dto.CompleteKakaoWebLoginCommand;
import com.orbit.auth.application.port.in.command.dto.ExchangeWebLoginCommand;
import com.orbit.auth.application.port.in.command.dto.StartKakaoWebLoginCommand;
import com.orbit.auth.application.port.in.command.dto.WebLoginCompletion;
import com.orbit.auth.application.port.in.command.dto.WebLoginStartInfo;
import com.orbit.auth.domain.OAuthProvider;
import com.orbit.shared.error.BusinessException;
import com.orbit.shared.error.CommonErrorCode;

/**
 * 웹(총관리자·직원)용 카카오 로그인. 카카오 콜백은 최상위 GET 이동이라 시작한 브라우저를 확인하는 연결 쿠키를 {@code SameSite=Lax; Secure; HttpOnly}로
 * 카카오 로그인 경로에만 보낸다. 콜백은 Access Token 대신 일회성 교환 코드만 복귀 주소에 실어 토큰이 URL·브라우저 기록에 남지 않게 한다.
 */
@RestController
@RequestMapping("/api/v1/auth/kakao")
public class KakaoWebLoginController implements KakaoWebLoginControllerDocs {

    static final String BROWSER_BINDING_COOKIE = "kakao_login_binding";
    static final String COOKIE_PATH = "/api/v1/auth/kakao";
    private static final String PKCE_METHOD = "S256";

    private final StartKakaoWebLoginUseCase startKakaoWebLoginUseCase;
    private final CompleteKakaoWebLoginUseCase completeKakaoWebLoginUseCase;
    private final ExchangeWebLoginUseCase exchangeWebLoginUseCase;
    private final KakaoWebReturnProperties returnProperties;

    public KakaoWebLoginController(
            StartKakaoWebLoginUseCase startKakaoWebLoginUseCase,
            CompleteKakaoWebLoginUseCase completeKakaoWebLoginUseCase,
            ExchangeWebLoginUseCase exchangeWebLoginUseCase,
            KakaoWebReturnProperties returnProperties) {
        this.startKakaoWebLoginUseCase = startKakaoWebLoginUseCase;
        this.completeKakaoWebLoginUseCase = completeKakaoWebLoginUseCase;
        this.exchangeWebLoginUseCase = exchangeWebLoginUseCase;
        this.returnProperties = returnProperties;
    }

    @Override
    @GetMapping("/authorize")
    public ResponseEntity<Void> authorize(
            @RequestParam("client") String client,
            @RequestParam("code_challenge") String codeChallenge,
            @RequestParam(name = "code_challenge_method", defaultValue = PKCE_METHOD) String codeChallengeMethod) {
        String returnUri = returnProperties
                .returnUri(client)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.BAD_REQUEST));
        if (!PKCE_METHOD.equals(codeChallengeMethod)) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST);
        }
        WebLoginStartInfo started =
                startKakaoWebLoginUseCase.start(new StartKakaoWebLoginCommand(returnUri, codeChallenge));
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(started.authorizationUri())
                .header(
                        HttpHeaders.SET_COOKIE,
                        bindingCookie(started.browserBinding(), started.bindingLifetime())
                                .toString())
                .build();
    }

    @Override
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(
            @RequestParam(name = "state", required = false) String state,
            @RequestParam(name = "code", required = false) String code,
            @RequestParam(name = "error", required = false) String error,
            @CookieValue(name = BROWSER_BINDING_COOKIE, required = false) String browserBinding) {
        WebLoginCompletion completion = completeKakaoWebLoginUseCase.complete(
                new CompleteKakaoWebLoginCommand(state, browserBinding, code, error));
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(WebLoginResponses.callbackLocation(completion))
                .header(HttpHeaders.SET_COOKIE, bindingCookie("", Duration.ZERO).toString())
                .build();
    }

    @Override
    @PostMapping("/exchange")
    public LoginResponse exchange(@Valid @RequestBody KakaoLoginExchangeRequest request) {
        return LoginResponse.from(exchangeWebLoginUseCase.exchange(
                new ExchangeWebLoginCommand(OAuthProvider.KAKAO, request.code(), request.codeVerifier())));
    }

    private static ResponseCookie bindingCookie(String value, Duration maxAge) {
        return WebLoginResponses.bindingCookie(BROWSER_BINDING_COOKIE, COOKIE_PATH, "Lax", value, maxAge);
    }
}
