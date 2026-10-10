package com.orbit.profile.domain;

/** 계정이 서비스를 쓰기 전에 마쳐야 할 다음 가입 단계. 클라이언트는 이 값으로 다시 시작할 화면을 고른다. */
public enum SignupStep {
    PROFILE,
    TERMS,
    COMPLETED
}
