package com.orbit.profile.domain;

/**
 * 계정의 이름. 앞뒤 공백을 지운 값을 쓰고 길이는 문자 수(코드 포인트)로 센다. 완성형 한글 음절과 영문자만 받으므로 숫자·특수문자·가운데 공백·자모·이모지는
 * 모두 거부된다.
 */
public record PersonName(String value) {

    private static final int MIN_LENGTH = 2;
    private static final int MAX_LENGTH = 20;
    private static final int FIRST_HANGUL_SYLLABLE = 0xAC00;
    private static final int LAST_HANGUL_SYLLABLE = 0xD7A3;

    public PersonName {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        value = value.strip();
        int length = value.codePointCount(0, value.length());
        if (length < MIN_LENGTH || length > MAX_LENGTH) {
            throw new IllegalArgumentException("name must be 2 to 20 characters");
        }
        if (!value.codePoints().allMatch(PersonName::isAllowed)) {
            throw new IllegalArgumentException("name must contain only Hangul syllables and Latin letters");
        }
    }

    private static boolean isAllowed(int codePoint) {
        return (codePoint >= FIRST_HANGUL_SYLLABLE && codePoint <= LAST_HANGUL_SYLLABLE)
                || (codePoint >= 'A' && codePoint <= 'Z')
                || (codePoint >= 'a' && codePoint <= 'z');
    }
}
