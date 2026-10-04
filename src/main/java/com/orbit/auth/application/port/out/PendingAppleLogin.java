package com.orbit.auth.application.port.out;

import com.orbit.auth.domain.AccountId;

/** 웹·Android Apple 콜백을 마치고 클라이언트가 교환 코드로 Access Token을 받아 가기를 기다리는 로그인. */
public record PendingAppleLogin(AccountId accountId, boolean registered) {}
