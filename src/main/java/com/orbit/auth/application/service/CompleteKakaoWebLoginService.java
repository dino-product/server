package com.orbit.auth.application.service;

import java.time.Clock;

import org.springframework.stereotype.Service;

import com.orbit.auth.application.error.AuthErrorCode;
import com.orbit.auth.application.port.in.command.CompleteKakaoWebLoginUseCase;
import com.orbit.auth.application.port.in.command.dto.CompleteKakaoWebLoginCommand;
import com.orbit.auth.application.port.in.command.dto.WebLoginCompletion;
import com.orbit.auth.application.port.out.AccountRepository;
import com.orbit.auth.application.port.out.ExchangeKakaoAuthorizationCodePort;
import com.orbit.auth.application.port.out.KakaoIdTokenClaims;
import com.orbit.auth.application.port.out.KakaoTokenApiException;
import com.orbit.auth.application.port.out.LoginExchangePort;
import com.orbit.auth.application.port.out.VerifyKakaoIdTokenPort;
import com.orbit.auth.application.port.out.WebLoginState;
import com.orbit.auth.application.port.out.WebLoginStatePort;
import com.orbit.auth.domain.ExternalIdentity;
import com.orbit.auth.domain.HashedNonce;
import com.orbit.auth.domain.OAuthProvider;
import com.orbit.shared.error.BusinessException;
import com.orbit.shared.error.CommonErrorCode;

import lombok.extern.slf4j.Slf4j;

/**
 * 카카오가 웹 로그인 콜백으로 돌려보낸 인가 결과를 마친다. state를 로그인을 시작한 브라우저의 연결 값과 함께 한 번만 소비한 뒤({@link WebLoginStates}),
 * 인가 코드를 카카오 토큰 API에서 교환해 받은 id_token을 검증하고 state의 nonce와 대조한다. 계정을 찾거나 등록한 다음, Access Token 대신 일회성 교환
 * 코드({@link WebLoginExchangeCodes})를 만들어 클라이언트 복귀 주소로 돌려보낸다. id_token과 카카오 토큰은 보관하지 않는다.
 *
 * <p>state가 없거나 다른 브라우저에서 온 콜백은 복귀 주소를 믿을 수 없으므로 AUTH-003으로 끝낸다. 그 밖의 실패는 복귀 주소에 오류 코드만 실어 보낸다
 * (카카오가 보낸 오류는 취소면 AUTH-008, 그 밖은 AUTH-010, 인가 코드가 없거나 카카오가 거절했거나 id_token이 유효하지 않으면 AUTH-002, nonce
 * 불일치 AUTH-003, 카카오 장애·설정 오류 AUTH-010, 예상하지 못한 내부 오류 COMMON-500). 카카오 토큰 API 호출 동안 DB 커넥션을 잡지 않도록 메서드
 * 트랜잭션을 두지 않는다.
 */
@Slf4j
@Service
public class CompleteKakaoWebLoginService implements CompleteKakaoWebLoginUseCase {

    private static final String USER_CANCELLED = "access_denied";

    private final WebLoginStates webLogins;
    private final ExchangeKakaoAuthorizationCodePort codeExchanges;
    private final VerifyKakaoIdTokenPort idTokens;
    private final WebLoginExchangeCodes exchangeCodes;
    private final AccountLogin accountLogin;

    public CompleteKakaoWebLoginService(
            WebLoginStatePort states,
            ExchangeKakaoAuthorizationCodePort codeExchanges,
            VerifyKakaoIdTokenPort idTokens,
            LoginExchangePort loginExchanges,
            AccountRepository accounts,
            SessionTokenIssuer tokens,
            Clock clock) {
        this.webLogins = new WebLoginStates(OAuthProvider.KAKAO, states);
        this.codeExchanges = codeExchanges;
        this.idTokens = idTokens;
        this.exchangeCodes = new WebLoginExchangeCodes(OAuthProvider.KAKAO, loginExchanges);
        this.accountLogin = new AccountLogin(accounts, tokens, clock);
    }

    @Override
    public WebLoginCompletion complete(CompleteKakaoWebLoginCommand command) {
        WebLoginState pending = webLogins.consume(command.state(), command.browserBinding());
        if (command.kakaoError() != null) {
            AuthErrorCode error = USER_CANCELLED.equals(command.kakaoError())
                    ? AuthErrorCode.LOGIN_CANCELLED
                    : AuthErrorCode.KAKAO_UNAVAILABLE;
            return WebLoginCompletion.failure(pending.returnUri(), error.getCode());
        }
        try {
            return WebLoginCompletion.success(pending.returnUri(), signIn(pending, command.authorizationCode()));
        } catch (BusinessException exception) {
            return WebLoginCompletion.failure(
                    pending.returnUri(), exception.getErrorCode().getCode());
        } catch (RuntimeException exception) {
            // 브라우저가 콜백 주소의 오류 본문에 멈추지 않도록 복귀 주소로 돌려보낸다.
            log.error("Kakao web login callback failed unexpectedly", exception);
            return WebLoginCompletion.failure(pending.returnUri(), CommonErrorCode.INTERNAL_SERVER_ERROR.getCode());
        }
    }

    private String signIn(WebLoginState pending, String authorizationCode) {
        if (authorizationCode == null || authorizationCode.isBlank()) {
            throw new BusinessException(AuthErrorCode.INVALID_ID_TOKEN);
        }
        KakaoIdTokenClaims claims = idTokens.verify(exchange(authorizationCode))
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_ID_TOKEN));
        if (!HashedNonce.fromClaim(claims.nonce()).map(pending.nonce()::equals).orElse(false)) {
            throw new BusinessException(AuthErrorCode.INVALID_NONCE);
        }
        SignedInAccount account =
                accountLogin.findOrRegister(new ExternalIdentity(OAuthProvider.KAKAO, claims.subject()));
        return exchangeCodes.issue(account, pending.codeChallenge());
    }

    private String exchange(String authorizationCode) {
        try {
            return codeExchanges.exchange(authorizationCode);
        } catch (KakaoTokenApiException exception) {
            AuthErrorCode code = exception.failure() == KakaoTokenApiException.Failure.REJECTED
                    ? AuthErrorCode.INVALID_ID_TOKEN
                    : AuthErrorCode.KAKAO_UNAVAILABLE;
            throw new BusinessException(code, exception);
        }
    }
}
