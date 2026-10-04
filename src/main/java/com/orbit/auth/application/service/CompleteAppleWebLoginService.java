package com.orbit.auth.application.service;

import java.time.Clock;
import java.time.Duration;

import org.springframework.stereotype.Service;

import com.orbit.auth.application.error.AuthErrorCode;
import com.orbit.auth.application.port.in.command.CompleteAppleWebLoginUseCase;
import com.orbit.auth.application.port.in.command.dto.AppleWebLoginCompletion;
import com.orbit.auth.application.port.in.command.dto.CompleteAppleWebLoginCommand;
import com.orbit.auth.application.port.out.AccessTokenPort;
import com.orbit.auth.application.port.out.AccountRepository;
import com.orbit.auth.application.port.out.AppleCodeExchange;
import com.orbit.auth.application.port.out.AppleIdTokenClaims;
import com.orbit.auth.application.port.out.AppleLoginExchangePort;
import com.orbit.auth.application.port.out.AppleRefreshTokenRepository;
import com.orbit.auth.application.port.out.AppleWebAuthorizationPort;
import com.orbit.auth.application.port.out.AppleWebLoginState;
import com.orbit.auth.application.port.out.AppleWebLoginStatePort;
import com.orbit.auth.application.port.out.ExchangeAppleAuthorizationCodePort;
import com.orbit.auth.application.port.out.PendingAppleLogin;
import com.orbit.auth.application.port.out.VerifyAppleIdTokenPort;
import com.orbit.auth.domain.ExternalIdentity;
import com.orbit.auth.domain.HashedNonce;
import com.orbit.auth.domain.OAuthProvider;
import com.orbit.shared.error.BusinessException;

/**
 * Apple이 form_post로 보낸 웹·Android 로그인 콜백을 마친다. state를 로그인을 시작한 브라우저의 연결 값과 함께 한 번만 소비한 뒤, id_token(웹 Services
 * ID 대상, state의 nonce)을 검증하고 code를 같은 클라이언트·콜백 주소로 교환해 같은 사용자인지 확인한다. 계정을 찾거나 등록하고 refresh token을 보관한
 * 다음, Access Token 대신 60초짜리 일회성 교환 코드를 만들어 클라이언트 복귀 주소로 돌려보낸다. 교환 코드는 시작한 클라이언트의 PKCE challenge에 묶여
 * 복귀 주소를 가로채도 code_verifier 없이 쓸 수 없다.
 *
 * <p>state가 없거나 다른 브라우저에서 온 콜백은 복귀 주소를 믿을 수 없으므로 AUTH-003으로 끝낸다. 그 밖의 실패는 복귀 주소에 오류 코드만 실어 보낸다. Apple 토큰
 * API 호출 동안 DB 커넥션을 잡지 않도록 메서드 트랜잭션을 두지 않는다.
 */
@Service
public class CompleteAppleWebLoginService implements CompleteAppleWebLoginUseCase {

    static final Duration EXCHANGE_CODE_TTL = Duration.ofSeconds(60);

    private final AppleWebLoginStatePort states;
    private final AppleWebAuthorizationPort authorization;
    private final VerifyAppleIdTokenPort idTokens;
    private final AppleAuthorizationCodeExchange codeExchange;
    private final AppleRefreshTokenRepository refreshTokens;
    private final AppleLoginExchangePort loginExchanges;
    private final AccountLogin accountLogin;
    private final Clock clock;

    public CompleteAppleWebLoginService(
            AppleWebLoginStatePort states,
            AppleWebAuthorizationPort authorization,
            VerifyAppleIdTokenPort idTokens,
            ExchangeAppleAuthorizationCodePort codeExchanges,
            AppleRefreshTokenRepository refreshTokens,
            AppleLoginExchangePort loginExchanges,
            AccountRepository accounts,
            AccessTokenPort accessTokens,
            Clock clock) {
        this.states = states;
        this.authorization = authorization;
        this.idTokens = idTokens;
        this.codeExchange = new AppleAuthorizationCodeExchange(codeExchanges);
        this.refreshTokens = refreshTokens;
        this.loginExchanges = loginExchanges;
        this.accountLogin = new AccountLogin(accounts, accessTokens, clock);
        this.clock = clock;
    }

    @Override
    public AppleWebLoginCompletion complete(CompleteAppleWebLoginCommand command) {
        if (isBlank(command.state()) || isBlank(command.browserBinding())) {
            throw new BusinessException(AuthErrorCode.INVALID_NONCE);
        }
        AppleWebLoginState pending = states.consume(
                        command.state(),
                        HashedNonce.fromRaw(command.browserBinding()).value())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_NONCE));
        if (command.appleError() != null) {
            return AppleWebLoginCompletion.failure(pending.returnUri(), AuthErrorCode.APPLE_LOGIN_CANCELLED.getCode());
        }
        try {
            return AppleWebLoginCompletion.success(pending.returnUri(), signIn(pending, command));
        } catch (BusinessException exception) {
            return AppleWebLoginCompletion.failure(
                    pending.returnUri(), exception.getErrorCode().getCode());
        }
    }

    private String signIn(AppleWebLoginState pending, CompleteAppleWebLoginCommand command) {
        AppleIdTokenClaims claims = idTokens.verify(command.idToken())
                .filter(verified -> verified.clientId().equals(authorization.clientId()))
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_ID_TOKEN));
        if (!HashedNonce.fromClaim(claims.nonce()).map(pending.nonce()::equals).orElse(false)) {
            throw new BusinessException(AuthErrorCode.INVALID_NONCE);
        }
        if (isBlank(command.authorizationCode())) {
            throw new BusinessException(AuthErrorCode.INVALID_APPLE_AUTHORIZATION_CODE);
        }
        AppleCodeExchange exchange = codeExchange.exchange(
                claims.subject(), claims.clientId(), command.authorizationCode(), authorization.redirectUri());
        SignedInAccount account =
                accountLogin.findOrRegister(new ExternalIdentity(OAuthProvider.APPLE, claims.subject()));
        refreshTokens.save(account.accountId(), exchange.clientId(), exchange.refreshToken(), clock.instant());
        String exchangeCode = LoginSecrets.random();
        loginExchanges.save(
                exchangeCode,
                new PendingAppleLogin(account.accountId(), account.registered(), pending.codeChallenge()),
                EXCHANGE_CODE_TTL);
        return exchangeCode;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
