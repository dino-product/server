package com.orbit.auth.application.service;

import com.orbit.auth.application.error.AuthErrorCode;
import com.orbit.auth.application.port.out.AppleCodeExchange;
import com.orbit.auth.application.port.out.AppleTokenApiException;
import com.orbit.auth.application.port.out.ExchangeAppleAuthorizationCodePort;
import com.orbit.shared.error.BusinessException;

/**
 * Apple authorization code를 교환하고, 교환 결과가 로그인 증명(id_token)과 같은 사용자의 것인지 확인한다. Apple이 code를 거절했거나 다른 사용자의
 * code면 AUTH-005, Apple을 쓸 수 없으면 AUTH-006이다.
 */
final class AppleAuthorizationCodeExchange {

    private final ExchangeAppleAuthorizationCodePort codeExchanges;

    AppleAuthorizationCodeExchange(ExchangeAppleAuthorizationCodePort codeExchanges) {
        this.codeExchanges = codeExchanges;
    }

    AppleCodeExchange exchange(String expectedSubject, String clientId, String authorizationCode, String redirectUri) {
        AppleCodeExchange exchange;
        try {
            exchange = codeExchanges.exchange(clientId, authorizationCode, redirectUri);
        } catch (AppleTokenApiException exception) {
            AuthErrorCode code = exception.failure() == AppleTokenApiException.Failure.REJECTED
                    ? AuthErrorCode.INVALID_APPLE_AUTHORIZATION_CODE
                    : AuthErrorCode.APPLE_UNAVAILABLE;
            throw new BusinessException(code, exception);
        }
        if (!expectedSubject.equals(exchange.subject())) {
            throw new BusinessException(AuthErrorCode.INVALID_APPLE_AUTHORIZATION_CODE);
        }
        return exchange;
    }
}
