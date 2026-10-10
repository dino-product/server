package com.orbit.profile.domain;

import java.util.regex.Pattern;

/**
 * 휴대전화 번호. 입력에서 숫자만 남겨 010으로 시작하는 11자리면 받고 그 숫자로 저장한다([조직·계정] 정책 §5). 본인확인이 없어 계정 사이에 같은 번호를 허용하므로
 * 식별자로 쓰지 않는다.
 */
public record PhoneNumber(String value) {

    private static final Pattern NON_DIGITS = Pattern.compile("[^0-9]");
    private static final Pattern MOBILE_NUMBER = Pattern.compile("010[0-9]{8}");

    public PhoneNumber {
        if (value == null) {
            throw new IllegalArgumentException("phoneNumber must not be null");
        }
        value = NON_DIGITS.matcher(value).replaceAll("");
        if (!MOBILE_NUMBER.matcher(value).matches()) {
            throw new IllegalArgumentException("phoneNumber must be 11 digits starting with 010");
        }
    }
}
