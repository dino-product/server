package com.orbit.auth.application.port.out;

public interface ExchangeKakaoAuthorizationCodePort {

    /**
     * 웹 로그인 콜백의 인가 코드를 같은 앱 키·콜백 주소로 카카오 토큰 API에서 교환해 받은 id_token을 돌려준다. id_token 검증은 호출자가 한다. 카카오 access·refresh
     * 토큰은 돌려주지 않는다.
     *
     * @throws KakaoTokenApiException 카카오가 거절했거나 호출하지 못했을 때
     */
    String exchange(String authorizationCode);
}
