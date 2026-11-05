package com.orbit.auth.adapter.in.web;

import java.net.URI;
import java.time.Duration;

import org.springframework.http.ResponseCookie;
import org.springframework.web.util.UriComponentsBuilder;

import com.orbit.auth.application.port.in.command.dto.WebLoginCompletion;

/** 서버가 제공자 인가·콜백을 맡는 웹 로그인의 응답 조각. 시작 브라우저 연결 쿠키와 콜백 뒤 복귀 주소를 만든다. */
final class WebLoginResponses {

    private WebLoginResponses() {}

    /** 연결 값은 스크립트로 읽을 수 없고 HTTPS로만, 해당 제공자의 로그인 경로({@code path})에만 보낸다. */
    static ResponseCookie bindingCookie(String name, String path, String sameSite, String value, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(true)
                .sameSite(sameSite)
                .path(path)
                .maxAge(maxAge)
                .build();
    }

    /** 성공이면 교환 코드({@code code}), 실패면 오류 코드({@code error})만 복귀 주소에 싣는다. Access Token은 URL에 싣지 않는다. */
    static URI callbackLocation(WebLoginCompletion completion) {
        return completion.exchangeCode() != null
                ? withQuery(completion.returnUri(), "code", completion.exchangeCode())
                : withQuery(completion.returnUri(), "error", completion.errorCode());
    }

    private static URI withQuery(String uri, String name, String value) {
        return UriComponentsBuilder.fromUriString(uri)
                .queryParam(name, value)
                .encode()
                .build()
                .toUri();
    }
}
