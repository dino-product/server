package com.orbit.auth.adapter.out.kakao;

import java.net.URI;

import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import com.orbit.auth.application.port.out.KakaoWebAuthorizationPort;
import com.orbit.auth.domain.HashedNonce;

/**
 * 카카오 인가 주소를 만든다. {@code openid} 범위로 요청해 토큰 응답에 id_token을 받는다. REST API 키가 id_token 허용 앱 키 목록에 없으면 교환한
 * id_token을 검증할 수 없으므로 기동하지 않는다. 같은 카카오 앱의 키여야 앱 로그인과 회원번호(sub)가 같아 같은 계정이 된다.
 */
@Component
class KakaoWebAuthorizationAdapter implements KakaoWebAuthorizationPort {

    private final KakaoWebLoginProperties properties;

    KakaoWebAuthorizationAdapter(KakaoWebLoginProperties properties, KakaoOidcProperties oidcProperties) {
        if (!oidcProperties.allowedAudiences().contains(properties.restApiKey())) {
            throw new IllegalArgumentException(
                    "app.auth.kakao.web.rest-api-key must be listed in app.auth.kakao.allowed-audiences");
        }
        this.properties = properties;
    }

    @Override
    public URI authorizationUri(String state, HashedNonce nonce) {
        return UriComponentsBuilder.fromUriString(properties.authorizationUri())
                .queryParam("response_type", "code")
                .queryParam("client_id", properties.restApiKey())
                .queryParam("redirect_uri", properties.redirectUri())
                .queryParam("scope", "openid")
                .queryParam("state", state)
                .queryParam("nonce", nonce.value())
                .encode()
                .build()
                .toUri();
    }
}
