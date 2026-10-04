package com.orbit.auth.application.service;

import java.time.Duration;

import org.springframework.stereotype.Service;

import com.orbit.auth.application.port.in.command.StartAppleWebLoginUseCase;
import com.orbit.auth.application.port.in.command.dto.AppleWebLoginStartInfo;
import com.orbit.auth.application.port.in.command.dto.StartAppleWebLoginCommand;
import com.orbit.auth.application.port.out.AppleWebAuthorizationPort;
import com.orbit.auth.application.port.out.AppleWebLoginState;
import com.orbit.auth.application.port.out.AppleWebLoginStatePort;
import com.orbit.auth.domain.HashedNonce;

/**
 * 웹·Android의 Apple 로그인을 시작한다. 일회성 state와 nonce, 시작한 브라우저에만 남길 연결 값을 만들고, state에 nonce 해시·연결 값 해시·복귀 주소를
 * 묶어 10분 보관한다. 인가 요청에는 nonce 해시를 실어 id_token의 nonce 클레임과 바로 대조한다. DB 트랜잭션 없이 state 저장소만 사용한다.
 */
@Service
public class StartAppleWebLoginService implements StartAppleWebLoginUseCase {

    static final Duration STATE_TTL = Duration.ofMinutes(10);

    private final AppleWebLoginStatePort states;
    private final AppleWebAuthorizationPort authorization;

    public StartAppleWebLoginService(AppleWebLoginStatePort states, AppleWebAuthorizationPort authorization) {
        this.states = states;
        this.authorization = authorization;
    }

    @Override
    public AppleWebLoginStartInfo start(StartAppleWebLoginCommand command) {
        String state = LoginSecrets.random();
        HashedNonce nonce = HashedNonce.fromRaw(LoginSecrets.random());
        String browserBinding = LoginSecrets.random();
        states.save(
                state,
                new AppleWebLoginState(
                        nonce, HashedNonce.fromRaw(browserBinding).value(), command.returnUri()),
                STATE_TTL);
        return new AppleWebLoginStartInfo(authorization.authorizationUri(state, nonce), browserBinding, STATE_TTL);
    }
}
