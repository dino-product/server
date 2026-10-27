package com.orbit.auth.application.service;

import org.springframework.stereotype.Service;

import com.orbit.auth.application.port.in.command.StartKakaoWebLoginUseCase;
import com.orbit.auth.application.port.in.command.dto.StartKakaoWebLoginCommand;
import com.orbit.auth.application.port.in.command.dto.WebLoginStartInfo;
import com.orbit.auth.application.port.out.KakaoWebAuthorizationPort;
import com.orbit.auth.application.port.out.WebLoginStatePort;
import com.orbit.auth.application.service.WebLoginStates.StartedWebLogin;
import com.orbit.auth.domain.OAuthProvider;

/**
 * 웹 카카오 로그인을 시작한다. 웹 로그인 state를 보관하고({@link WebLoginStates}), 인가 요청에는 nonce 해시를 실어 교환한 id_token의 nonce 클레임과
 * 바로 대조한다. DB 트랜잭션 없이 state 저장소만 사용한다.
 */
@Service
public class StartKakaoWebLoginService implements StartKakaoWebLoginUseCase {

    private final WebLoginStates webLogins;
    private final KakaoWebAuthorizationPort authorization;

    public StartKakaoWebLoginService(WebLoginStatePort states, KakaoWebAuthorizationPort authorization) {
        this.webLogins = new WebLoginStates(OAuthProvider.KAKAO, states);
        this.authorization = authorization;
    }

    @Override
    public WebLoginStartInfo start(StartKakaoWebLoginCommand command) {
        StartedWebLogin started = webLogins.start(command.returnUri(), command.codeChallenge());
        return new WebLoginStartInfo(
                authorization.authorizationUri(started.state(), started.nonce()),
                started.browserBinding(),
                WebLoginStates.TTL);
    }
}
