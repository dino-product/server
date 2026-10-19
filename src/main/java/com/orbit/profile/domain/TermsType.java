package com.orbit.profile.domain;

/** 가입 때 동의받는 약관 종류. 서비스 이용약관과 개인정보 처리방침은 필수, 마케팅 정보 수신은 선택이다. */
public enum TermsType {
    SERVICE(true),
    PRIVACY(true),
    MARKETING(false);

    private final boolean required;

    TermsType(boolean required) {
        this.required = required;
    }

    public boolean required() {
        return required;
    }
}
