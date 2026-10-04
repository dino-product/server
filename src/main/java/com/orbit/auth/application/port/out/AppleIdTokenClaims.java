package com.orbit.auth.application.port.out;

/** 서명·발급자·대상·만료를 통과한 Apple id_token에서 로그인에 필요한 클레임만 고른 값. nonce는 앱이 넣은 해시 그대로다. */
public record AppleIdTokenClaims(String subject, String nonce) {}
