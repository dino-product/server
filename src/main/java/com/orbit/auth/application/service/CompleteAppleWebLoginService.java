package com.orbit.auth.application.service;

import java.time.Clock;

import org.springframework.stereotype.Service;

import com.orbit.auth.application.error.AuthErrorCode;
import com.orbit.auth.application.port.in.command.CompleteAppleWebLoginUseCase;
import com.orbit.auth.application.port.in.command.dto.CompleteAppleWebLoginCommand;
import com.orbit.auth.application.port.in.command.dto.WebLoginCompletion;
import com.orbit.auth.application.port.out.AccountRepository;
import com.orbit.auth.application.port.out.AppleCodeExchange;
import com.orbit.auth.application.port.out.AppleIdTokenClaims;
import com.orbit.auth.application.port.out.AppleRefreshTokenRepository;
import com.orbit.auth.application.port.out.AppleWebAuthorizationPort;
import com.orbit.auth.application.port.out.ExchangeAppleAuthorizationCodePort;
import com.orbit.auth.application.port.out.LoginExchangePort;
import com.orbit.auth.application.port.out.VerifyAppleIdTokenPort;
import com.orbit.auth.application.port.out.WebLoginState;
import com.orbit.auth.application.port.out.WebLoginStatePort;
import com.orbit.auth.domain.ExternalIdentity;
import com.orbit.auth.domain.HashedNonce;
import com.orbit.auth.domain.OAuthProvider;
import com.orbit.shared.error.BusinessException;
import com.orbit.shared.error.CommonErrorCode;

import lombok.extern.slf4j.Slf4j;

/**
 * Apple이 form_post로 보낸 웹·Android 로그인 콜백을 마친다. state를 로그인을 시작한 브라우저의 연결 값과 함께 한 번만 소비한 뒤({@link
 * WebLoginStates}), id_token(웹 Services ID 대상, state의 nonce)을 검증하고 code를 같은 클라이언트·콜백 주소로 교환해 같은 사용자인지 확인한다.
 * 계정을 찾거나 등록하고 refresh token을 보관한 다음, Access Token 대신 일회성 교환 코드({@link WebLoginExchangeCodes})를 만들어 클라이언트 복귀
 * 주소로 돌려보낸다.
 *
 * <p>state가 없거나 다른 브라우저에서 온 콜백은 복귀 주소를 믿을 수 없으므로 AUTH-003으로 끝낸다. 그 밖의 실패는 복귀 주소에 오류 코드만 실어 보낸다
 * (Apple이 보낸 오류는 취소면 AUTH-008, 그 밖은 AUTH-006, 예상하지 못한 내부 오류는 COMMON-500). Apple 토큰
 * API 호출 동안 DB 커넥션을 잡지 않도록 메서드 트랜잭션을 두지 않는다.
 */
@Slf4j
@Service
public class CompleteAppleWebLoginService implements CompleteAppleWebLoginUseCase {

    private static final String USER_CANCELLED = "user_cancelled_authorize";

    private final WebLoginStates webLogins;
    private final AppleWebAuthorizationPort authorization;
    private final VerifyAppleIdTokenPort idTokens;
    private final AppleAuthorizationCodeExchange codeExchange;
    private final AppleRefreshTokenRepository refreshTokens;
    private final WebLoginExchangeCodes exchangeCodes;
    private final AccountLogin accountLogin;
    private final Clock clock;

    public CompleteAppleWebLoginService(
            WebLoginStatePort states,
            AppleWebAuthorizationPort authorization,
            VerifyAppleIdTokenPort idTokens,
            ExchangeAppleAuthorizationCodePort codeExchanges,
            AppleRefreshTokenRepository refreshTokens,
            LoginExchangePort loginExchanges,
            AccountRepository accounts,
            SessionTokenIssuer tokens,
            Clock clock) {
        this.webLogins = new WebLoginStates(OAuthProvider.APPLE, states);
        this.authorization = authorization;
        this.idTokens = idTokens;
        this.codeExchange = new AppleAuthorizationCodeExchange(codeExchanges);
        this.refreshTokens = refreshTokens;
        this.exchangeCodes = new WebLoginExchangeCodes(OAuthProvider.APPLE, loginExchanges);
        this.accountLogin = new AccountLogin(accounts, tokens, clock);
        this.clock = clock;
    }

    @Override
    public WebLoginCompletion complete(CompleteAppleWebLoginCommand command) {
        WebLoginState pending = webLogins.consume(command.state(), command.browserBinding());
        if (command.appleError() != null) {
            AuthErrorCode error = USER_CANCELLED.equals(command.appleError())
                    ? AuthErrorCode.LOGIN_CANCELLED
                    : AuthErrorCode.APPLE_UNAVAILABLE;
            return WebLoginCompletion.failure(pending.returnUri(), error.getCode());
        }
        try {
            return WebLoginCompletion.success(pending.returnUri(), signIn(pending, command));
        } catch (BusinessException exception) {
            return WebLoginCompletion.failure(
                    pending.returnUri(), exception.getErrorCode().getCode());
        } catch (RuntimeException exception) {
            // 브라우저(Android Custom Tab)가 콜백 주소의 오류 본문에 멈추지 않도록 복귀 주소로 돌려보낸다.
            log.error("Apple web login callback failed unexpectedly", exception);
            return WebLoginCompletion.failure(pending.returnUri(), CommonErrorCode.INTERNAL_SERVER_ERROR.getCode());
        }
    }

    private String signIn(WebLoginState pending, CompleteAppleWebLoginCommand command) {
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
        return exchangeCodes.issue(account, pending.codeChallenge());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
