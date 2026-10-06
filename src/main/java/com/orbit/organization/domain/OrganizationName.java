package com.orbit.organization.domain;

/**
 * 발주사명. 앞뒤 공백을 지운 값을 쓰고, 길이는 문자 수(코드 포인트)로 센다. 이모지는 받지 않는다 — 유니코드 Extended_Pictographic·Emoji_Presentation
 * 문자(국기 문자, ©·®·™ 포함)와 이모지 표시 선택자·키캡 결합 문자를 거부한다.
 */
public record OrganizationName(String value) {

    private static final int MIN_LENGTH = 2;
    private static final int MAX_LENGTH = 30;
    private static final int EMOJI_VARIATION_SELECTOR = 0xFE0F;
    private static final int COMBINING_ENCLOSING_KEYCAP = 0x20E3;

    public OrganizationName {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("organizationName must not be blank");
        }
        value = value.strip();
        int length = value.codePointCount(0, value.length());
        if (length < MIN_LENGTH || length > MAX_LENGTH) {
            throw new IllegalArgumentException("organizationName must be 2 to 30 characters");
        }
        if (value.codePoints().anyMatch(OrganizationName::isEmoji)) {
            throw new IllegalArgumentException("organizationName must not contain emoji");
        }
    }

    private static boolean isEmoji(int codePoint) {
        return Character.isExtendedPictographic(codePoint)
                || Character.isEmojiPresentation(codePoint)
                || codePoint == EMOJI_VARIATION_SELECTOR
                || codePoint == COMBINING_ENCLOSING_KEYCAP;
    }
}
