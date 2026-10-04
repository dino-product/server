package com.orbit.auth.adapter.out.apple;

import java.net.URI;

import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import com.orbit.auth.application.port.out.AppleWebAuthorizationPort;
import com.orbit.auth.domain.HashedNonce;

/**
 * Apple 인가 주소를 만든다. code와 id_token을 함께 받고, 이름·이메일 범위를 요청할 때 Apple이 요구하는 {@code form_post}로 콜백에 받는다. 이름·이메일은
 * 저장하지 않는다. 웹 Services ID가 id_token 허용 목록에 없으면 콜백 id_token을 받을 수 없으므로 기동하지 않는다.
 */
@Component
class AppleWebAuthorizationAdapter implements AppleWebAuthorizationPort {

    private final AppleWebLoginProperties properties;

    AppleWebAuthorizationAdapter(AppleWebLoginProperties properties, AppleOidcProperties oidcProperties) {
        if (!oidcProperties.allowedAudiences().contains(properties.servicesId())) {
            throw new IllegalArgumentException(
                    "app.auth.apple.web.services-id must be listed in app.auth.apple.allowed-audiences");
        }
        this.properties = properties;
    }

    @Override
    public URI authorizationUri(String state, HashedNonce nonce) {
        return UriComponentsBuilder.fromUriString(properties.authorizationUri())
                .queryParam("response_type", "code id_token")
                .queryParam("response_mode", "form_post")
                .queryParam("client_id", properties.servicesId())
                .queryParam("redirect_uri", properties.redirectUri())
                .queryParam("scope", "name email")
                .queryParam("state", state)
                .queryParam("nonce", nonce.value())
                .encode()
                .build()
                .toUri();
    }

    @Override
    public String clientId() {
        return properties.servicesId();
    }

    @Override
    public String redirectUri() {
        return properties.redirectUri();
    }
}
