package com.orbit.auth.application.port.out;

import java.net.URI;

import com.orbit.auth.domain.HashedNonce;

/** 웹용 카카오 인가 요청을 만든다. 콜백의 인가 코드는 같은 앱 키·콜백 주소로 교환한다. */
public interface KakaoWebAuthorizationPort {

    URI authorizationUri(String state, HashedNonce nonce);
}
