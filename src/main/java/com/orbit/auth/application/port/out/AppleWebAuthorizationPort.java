package com.orbit.auth.application.port.out;

import java.net.URI;

import com.orbit.auth.domain.HashedNonce;

/** 웹·Android용 Apple 인가 요청을 만든다. 같은 클라이언트(Services ID)·콜백 주소로 콜백의 code를 교환한다. */
public interface AppleWebAuthorizationPort {

    URI authorizationUri(String state, HashedNonce nonce);

    String clientId();

    String redirectUri();
}
