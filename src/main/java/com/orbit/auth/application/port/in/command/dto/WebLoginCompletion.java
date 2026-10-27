package com.orbit.auth.application.port.in.command.dto;

/**
 * 웹 로그인 콜백 결과. 클라이언트는 {@code returnUri}로 돌아가 성공이면 일회성 {@code exchangeCode}로 Access Token을 받고, 실패면 오류
 * 코드({@code AUTH-xxx})만 받는다. 둘 중 하나만 값이 있다.
 */
public record WebLoginCompletion(String returnUri, String exchangeCode, String errorCode) {

    public static WebLoginCompletion success(String returnUri, String exchangeCode) {
        return new WebLoginCompletion(returnUri, exchangeCode, null);
    }

    public static WebLoginCompletion failure(String returnUri, String errorCode) {
        return new WebLoginCompletion(returnUri, null, errorCode);
    }
}
