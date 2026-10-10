package com.orbit.profile.domain;

/**
 * 계정이 서비스를 쓰기 전에 아직 마치지 않은 다음 단계. 프로필을 저장한 가입 미완료 계정은 {@link #TERMS}다. 다시 시작할 화면은 이 값만으로 고르지 않는다 —
 * 가입 미완료 계정이 다시 로그인하면 입력해 둔 값이 채워진 프로필 입력 화면부터 시작해 "완료" 뒤 약관 동의로 이어진다([조직·계정] 정책 §5).
 */
public enum SignupStep {
    PROFILE,
    TERMS,
    COMPLETED
}
