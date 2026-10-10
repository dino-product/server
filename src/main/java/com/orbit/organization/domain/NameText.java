package com.orbit.organization.domain;

/**
 * 이름 입력 공통 규칙([조직·계정] §5). 앞뒤 공백을 지운 값을 쓰고, 길이는 문자 수(코드 포인트)로 센다. 이모지는 받지 않는다 — 유니코드
 * Extended_Pictographic·Emoji_Presentation 문자(국기 문자, ©·®·™ 포함)와 이모지 표시 선택자·키캡 결합 문자를 거부한다. 제어 문자(NUL·줄바꿈·탭 등
 * {@link Character#isISOControl}), 줄·문단 구분자(U+2028·U+2029), 서식 문자(제로폭·방향 제어 등 유니코드 Cf)도 받지 않는다 — PostgreSQL은 NUL을
 * 저장하지 못하고, 나머지는 한 줄 이름을 깨뜨리거나 보이지 않게 같은 이름을 다르게 만든다(잠정 O-14).
 */
final class NameText {

    private static final int EMOJI_VARIATION_SELECTOR = 0xFE0F;
    private static final int COMBINING_ENCLOSING_KEYCAP = 0x20E3;

    private NameText() {}

    /** 규칙을 지키면 앞뒤 공백을 지운 값을 돌려주고, 어기면 {@code field} 이름을 담은 {@link IllegalArgumentException}을 던진다. */
    static String require(String value, int minLength, int maxLength, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        String stripped = value.strip();
        int length = stripped.codePointCount(0, stripped.length());
        if (length < minLength || length > maxLength) {
            throw new IllegalArgumentException(field + " must be " + minLength + " to " + maxLength + " characters");
        }
        if (stripped.codePoints().anyMatch(NameText::isEmoji)) {
            throw new IllegalArgumentException(field + " must not contain emoji");
        }
        if (stripped.codePoints().anyMatch(NameText::isInvisibleOrBreaking)) {
            throw new IllegalArgumentException(field + " must not contain control, separator or format characters");
        }
        return stripped;
    }

    private static boolean isInvisibleOrBreaking(int codePoint) {
        int type = Character.getType(codePoint);
        return Character.isISOControl(codePoint)
                || type == Character.LINE_SEPARATOR
                || type == Character.PARAGRAPH_SEPARATOR
                || type == Character.FORMAT;
    }

    private static boolean isEmoji(int codePoint) {
        return Character.isExtendedPictographic(codePoint)
                || Character.isEmojiPresentation(codePoint)
                || codePoint == EMOJI_VARIATION_SELECTOR
                || codePoint == COMBINING_ENCLOSING_KEYCAP;
    }
}
