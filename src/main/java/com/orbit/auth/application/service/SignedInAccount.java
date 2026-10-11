package com.orbit.auth.application.service;

import com.orbit.auth.domain.AccountId;

/** 로그인으로 확인한 계정. {@code registered}는 이번 로그인으로 계정이 처음 만들어졌는지를 뜻한다. */
record SignedInAccount(AccountId accountId, boolean registered) {}
