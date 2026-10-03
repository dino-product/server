package com.orbit.auth.application.port.out;

/** 서명·발급자·대상·만료를 통과한 카카오 id_token에서 로그인에 필요한 클레임만 고른 값. */
public record KakaoIdTokenClaims(String subject, String nonce) {}
