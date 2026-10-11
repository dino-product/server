package com.orbit.auth.application.service;

import java.security.SecureRandom;
import java.util.Base64;

/** 로그인 흐름에서 쓰는 추측 불가능한 일회성 값(state·nonce·브라우저 연결·교환 코드)을 만든다. */
final class LoginSecrets {

    private static final int BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private LoginSecrets() {}

    /** 32바이트 난수를 패딩 없는 base64url(43자)로 돌려준다. */
    static String random() {
        byte[] bytes = new byte[BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
