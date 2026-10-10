package com.orbit.profile.domain;

import java.util.regex.Pattern;

/**
 * 휴대전화 번호. 입력의 하이픈·공백을 지운 숫자 11자리로 저장하며 010으로 시작해야 한다. 그 밖의 문자는 지우지 않고 거부한다. 본인확인이 없어 계정 사이에 같은
 * 번호를 허용하므로 식별자로 쓰지 않는다.
 */
public record PhoneNumber(String value) {

    private static final Pattern SEPARATORS = Pattern.compile("[-\\s]");
    private static final Pattern MOBILE_NUMBER = Pattern.compile("010[0-9]{8}");

    public PhoneNumber {
        if (value == null) {
            throw new IllegalArgumentException("phoneNumber must not be null");
        }
        value = SEPARATORS.matcher(value).replaceAll("");
        if (!MOBILE_NUMBER.matcher(value).matches()) {
            throw new IllegalArgumentException("phoneNumber must be 11 digits starting with 010");
        }
    }
}
