package com.orbit.auth.application.service;

import java.time.Clock;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.auth.application.error.AuthErrorCode;
import com.orbit.auth.application.port.in.command.LoginWithKakaoUseCase;
import com.orbit.auth.application.port.in.command.dto.LoginInfo;
import com.orbit.auth.application.port.in.command.dto.LoginWithKakaoCommand;
import com.orbit.auth.application.port.out.AccessTokenPort;
import com.orbit.auth.application.port.out.AccountRepository;
import com.orbit.auth.application.port.out.DuplicateIdentityException;
import com.orbit.auth.application.port.out.IssuedAccessToken;
import com.orbit.auth.application.port.out.KakaoIdTokenClaims;
import com.orbit.auth.application.port.out.LoginNoncePort;
import com.orbit.auth.application.port.out.VerifyKakaoIdTokenPort;
import com.orbit.auth.domain.Account;
import com.orbit.auth.domain.ExternalIdentity;
import com.orbit.auth.domain.OAuthProvider;
import com.orbit.shared.error.BusinessException;

/**
 * 카카오 id_token으로 로그인한다. 서명·발급자·대상·만료를 검증한 뒤 서버가 발급한 nonce를 한 번만 소비하고, 외부 식별에 연결된 계정을 찾거나 처음이면
 * 등록한 뒤 자체 Access Token을 발급한다. id_token은 로그인 증명일 뿐 보관하지 않는다.
 */
@Service
public class LoginWithKakaoService implements LoginWithKakaoUseCase {

    private final VerifyKakaoIdTokenPort idTokens;
    private final LoginNoncePort nonces;
    private final AccountRepository accounts;
    private final AccessTokenPort accessTokens;
    private final Clock clock;

    public LoginWithKakaoService(
            VerifyKakaoIdTokenPort idTokens,
            LoginNoncePort nonces,
            AccountRepository accounts,
            AccessTokenPort accessTokens,
            Clock clock) {
        this.idTokens = idTokens;
        this.nonces = nonces;
        this.accounts = accounts;
        this.accessTokens = accessTokens;
        this.clock = clock;
    }

    @Override
    @Transactional
    public LoginInfo login(LoginWithKakaoCommand command) {
        KakaoIdTokenClaims claims = idTokens.verify(command.idToken())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_ID_TOKEN));
        if (!nonces.consume(claims.nonce())) {
            throw new BusinessException(AuthErrorCode.INVALID_NONCE);
        }
        ExternalIdentity identity = new ExternalIdentity(OAuthProvider.KAKAO, claims.subject());
        boolean registered = false;
        Account account = accounts.findByIdentity(identity).orElse(null);
        if (account == null) {
            try {
                account = accounts.saveNew(Account.register(identity, clock.instant()));
                registered = true;
            } catch (DuplicateIdentityException exception) {
                // 같은 사용자의 동시 첫 로그인에서 먼저 저장된 계정을 그대로 쓴다.
                account = accounts.findByIdentity(identity).orElseThrow(() -> exception);
            }
        }
        IssuedAccessToken issued = accessTokens.issue(account.id().orElseThrow());
        return new LoginInfo(
                account.id().orElseThrow().value(),
                registered,
                issued.value(),
                issued.token().expiresAt());
    }
}
