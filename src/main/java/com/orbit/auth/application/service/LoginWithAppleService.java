package com.orbit.auth.application.service;

import java.time.Clock;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.auth.application.error.AuthErrorCode;
import com.orbit.auth.application.port.in.command.LoginWithAppleUseCase;
import com.orbit.auth.application.port.in.command.dto.LoginInfo;
import com.orbit.auth.application.port.in.command.dto.LoginWithAppleCommand;
import com.orbit.auth.application.port.out.AccessTokenPort;
import com.orbit.auth.application.port.out.AccountRepository;
import com.orbit.auth.application.port.out.AppleIdTokenClaims;
import com.orbit.auth.application.port.out.AppleLoginNoncePort;
import com.orbit.auth.application.port.out.VerifyAppleIdTokenPort;
import com.orbit.auth.domain.ExternalIdentity;
import com.orbit.auth.domain.HashedNonce;
import com.orbit.auth.domain.OAuthProvider;
import com.orbit.shared.error.BusinessException;

/**
 * iOS 앱이 제출한 Apple id_token으로 로그인한다. 서명·발급자·대상·만료를 검증한 뒤 nonce 클레임(서버 발급 raw nonce의 해시)을 한 번만 소비하고, 외부 식별에
 * 연결된 계정을 찾거나 처음이면 등록한 뒤 자체 Access Token을 발급한다. id_token과 이름·이메일 클레임은 보관하지 않는다.
 */
@Service
public class LoginWithAppleService implements LoginWithAppleUseCase {

    private final VerifyAppleIdTokenPort idTokens;
    private final AppleLoginNoncePort nonces;
    private final AccountLogin accountLogin;

    public LoginWithAppleService(
            VerifyAppleIdTokenPort idTokens,
            AppleLoginNoncePort nonces,
            AccountRepository accounts,
            AccessTokenPort accessTokens,
            Clock clock) {
        this.idTokens = idTokens;
        this.nonces = nonces;
        this.accountLogin = new AccountLogin(accounts, accessTokens, clock);
    }

    @Override
    @Transactional
    public LoginInfo login(LoginWithAppleCommand command) {
        AppleIdTokenClaims claims = idTokens.verify(command.idToken())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_ID_TOKEN));
        HashedNonce nonce = HashedNonce.fromClaim(claims.nonce())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_NONCE));
        if (!nonces.consume(nonce)) {
            throw new BusinessException(AuthErrorCode.INVALID_NONCE);
        }
        return accountLogin.login(new ExternalIdentity(OAuthProvider.APPLE, claims.subject()));
    }
}
