package com.orbit.auth.application.service;

import java.time.Clock;

import org.springframework.stereotype.Service;

import com.orbit.auth.application.error.AuthErrorCode;
import com.orbit.auth.application.port.in.command.LoginWithAppleUseCase;
import com.orbit.auth.application.port.in.command.dto.LoginInfo;
import com.orbit.auth.application.port.in.command.dto.LoginWithAppleCommand;
import com.orbit.auth.application.port.out.AccessTokenPort;
import com.orbit.auth.application.port.out.AccountRepository;
import com.orbit.auth.application.port.out.AppleCodeExchange;
import com.orbit.auth.application.port.out.AppleIdTokenClaims;
import com.orbit.auth.application.port.out.AppleLoginNoncePort;
import com.orbit.auth.application.port.out.AppleRefreshTokenRepository;
import com.orbit.auth.application.port.out.AppleWebAuthorizationPort;
import com.orbit.auth.application.port.out.ExchangeAppleAuthorizationCodePort;
import com.orbit.auth.application.port.out.VerifyAppleIdTokenPort;
import com.orbit.auth.domain.AccountId;
import com.orbit.auth.domain.ExternalIdentity;
import com.orbit.auth.domain.HashedNonce;
import com.orbit.auth.domain.OAuthProvider;
import com.orbit.shared.error.BusinessException;

/**
 * iOS 앱이 제출한 Apple id_token과 authorization code로 로그인한다. id_token의 서명·발급자·대상·만료를 검증하고 nonce 클레임(서버 발급 raw nonce의
 * 해시)을 한 번만 소비한 뒤, code를 같은 클라이언트로 교환해 같은 사용자인지 확인한다. 외부 식별에 연결된 계정을 찾거나 처음이면 등록하고, 계정 삭제 때 토큰
 * 철회에 쓸 refresh token을 보관한 뒤 자체 Access Token을 발급한다.
 *
 * <p>Apple 토큰 API 호출 동안 DB 커넥션을 잡지 않도록 메서드 트랜잭션을 두지 않는다. 계정 등록은 저장소가 별도 트랜잭션으로 확정하고 refresh token은
 * 계정 확정 뒤 upsert하므로, 보관이 실패해도 다음 로그인에서 다시 저장된다. id_token과 이름·이메일 클레임은 보관하지 않는다.
 */
@Service
public class LoginWithAppleService implements LoginWithAppleUseCase {

    private final VerifyAppleIdTokenPort idTokens;
    private final AppleLoginNoncePort nonces;
    private final AppleAuthorizationCodeExchange codeExchange;
    private final AppleRefreshTokenRepository refreshTokens;
    private final AppleWebAuthorizationPort webAuthorization;
    private final AccountLogin accountLogin;
    private final Clock clock;

    public LoginWithAppleService(
            VerifyAppleIdTokenPort idTokens,
            AppleLoginNoncePort nonces,
            ExchangeAppleAuthorizationCodePort codeExchanges,
            AppleRefreshTokenRepository refreshTokens,
            AppleWebAuthorizationPort webAuthorization,
            AccountRepository accounts,
            AccessTokenPort accessTokens,
            Clock clock) {
        this.idTokens = idTokens;
        this.nonces = nonces;
        this.codeExchange = new AppleAuthorizationCodeExchange(codeExchanges);
        this.refreshTokens = refreshTokens;
        this.webAuthorization = webAuthorization;
        this.accountLogin = new AccountLogin(accounts, accessTokens, clock);
        this.clock = clock;
    }

    @Override
    public LoginInfo login(LoginWithAppleCommand command) {
        // 웹 Services ID의 code는 콜백 주소(redirect_uri)와 함께만 교환되므로 iOS 경로는 앱 클라이언트 토큰만 받는다.
        AppleIdTokenClaims claims = idTokens.verify(command.idToken())
                .filter(verified -> !verified.clientId().equals(webAuthorization.clientId()))
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_ID_TOKEN));
        HashedNonce nonce = HashedNonce.fromClaim(claims.nonce())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_NONCE));
        if (!nonces.consume(nonce)) {
            throw new BusinessException(AuthErrorCode.INVALID_NONCE);
        }
        AppleCodeExchange exchange =
                codeExchange.exchange(claims.subject(), claims.clientId(), command.authorizationCode(), null);
        LoginInfo login = accountLogin.login(new ExternalIdentity(OAuthProvider.APPLE, claims.subject()));
        refreshTokens.save(
                new AccountId(login.accountId()), exchange.clientId(), exchange.refreshToken(), clock.instant());
        return login;
    }
}
