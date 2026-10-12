package com.orbit.organization.domain;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 발주사의 회사 코드. 참여 링크·QR의 근거이며 저장 형식은 Crockford Base32 대문자 6자다(I·L·O·U 제외). 사용자 입력은 {@link #parse}로 읽는다.
 */
public record CompanyCode(String value) {

    public static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    public static final int LENGTH = 6;

    private static final Pattern FORMAT = Pattern.compile("[" + ALPHABET + "]{" + LENGTH + "}");
    private static final Pattern IGNORED_INPUT = Pattern.compile("[\\s-]");

    public CompanyCode {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("companyCode must be 6 Crockford Base32 characters");
        }
    }

    /** 대소문자를 무시하고 공백·하이픈을 지운 입력을 회사 코드로 읽는다. */
    public static CompanyCode parse(String input) {
        if (input == null) {
            throw new IllegalArgumentException("companyCode must not be null");
        }
        return new CompanyCode(IGNORED_INPUT.matcher(input).replaceAll("").toUpperCase(Locale.ROOT));
    }
}
