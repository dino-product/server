package com.orbit.profile.domain;

/** 계정의 가입 상태. 프로필이 없거나 필수 약관에 동의하기 전이면 가입 미완료다. 탈퇴하면 다시 바뀌지 않는다. */
public enum SignupStatus {
    PENDING_SIGNUP,
    ACTIVE,
    WITHDRAWN
}
