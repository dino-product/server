package com.orbit.auth.application.service;

import java.time.Duration;

import com.orbit.auth.application.port.out.LoginExchangePort;
import com.orbit.auth.application.port.out.PendingWebLogin;
import com.orbit.auth.domain.OAuthProvider;
import com.orbit.auth.domain.PkceChallenge;

/**
 * 웹 로그인 콜백이 Access Token 대신 복귀 주소에 싣는 60초짜리 일회성 교환 코드를 만든다. 코드는 로그인을 시작한 클라이언트의 PKCE challenge에 묶여 복귀
 * 주소를 가로채도 code_verifier 없이 쓸 수 없다.
 */
final class WebLoginExchangeCodes {

    static final Duration TTL = Duration.ofSeconds(60);

    private final OAuthProvider provider;
    private final LoginExchangePort loginExchanges;

    WebLoginExchangeCodes(OAuthProvider provider, LoginExchangePort loginExchanges) {
        this.provider = provider;
        this.loginExchanges = loginExchanges;
    }

    String issue(SignedInAccount account, PkceChallenge codeChallenge) {
        String exchangeCode = LoginSecrets.random();
        loginExchanges.save(
                provider,
                exchangeCode,
                new PendingWebLogin(account.accountId(), account.registered(), codeChallenge),
                TTL);
        return exchangeCode;
    }
}
