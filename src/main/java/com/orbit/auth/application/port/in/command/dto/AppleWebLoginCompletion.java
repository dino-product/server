package com.orbit.auth.application.port.in.command.dto;

/**
 * Apple 웹 로그인 콜백 결과. 클라이언트는 {@code returnUri}로 돌아가 성공이면 일회성 {@code exchangeCode}로 Access Token을 받고, 실패면 오류
 * 코드({@code AUTH-xxx})만 받는다. 둘 중 하나만 값이 있다.
 */
public record AppleWebLoginCompletion(String returnUri, String exchangeCode, String errorCode) {

    public static AppleWebLoginCompletion success(String returnUri, String exchangeCode) {
        return new AppleWebLoginCompletion(returnUri, exchangeCode, null);
    }

    public static AppleWebLoginCompletion failure(String returnUri, String errorCode) {
        return new AppleWebLoginCompletion(returnUri, null, errorCode);
    }
}
