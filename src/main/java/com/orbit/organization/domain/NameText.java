package com.orbit.organization.domain;

import java.text.Normalizer;
import java.util.Set;

/**
 * 이름 입력 공통 규칙([조직·계정] §5). 앞뒤 공백을 지운 값을 쓰고, 길이는 문자 수(코드 포인트)로 센다. 이모지는 받지 않는다 — 유니코드
 * Extended_Pictographic·Emoji_Presentation 문자(국기 문자, ©·®·™ 포함)와 이모지 표시 선택자·키캡 결합 문자를 거부한다.
 *
 * <p>보이지 않게 같은 이름이 달라지지 않도록 검사 전에 정리한다([조직·계정] §5 밖의 레포 쪽 정리): 붙여넣기에 섞이는 서식 문자(제로폭·방향 제어·BOM·소프트 하이픈 등 유니코드 Cf)는
 * 지우고, NBSP 같은 공백류(유니코드 Zs)는 일반 공백으로 바꾼 뒤 합친 형태(NFC)로 바꾼다. 제어 문자(NUL·줄바꿈·탭 등 {@link Character#isISOControl}),
 * 줄·문단 구분자(U+2028·U+2029), 글자로 분류되지만 보이지 않는 한글 채움 문자·점자 빈칸은 거부한다 — PostgreSQL은 NUL을 저장하지 못하고, 나머지는 한 줄
 * 이름을 깨뜨리거나 빈 이름을 만든다.
 */
final class NameText {

    private static final int EMOJI_VARIATION_SELECTOR = 0xFE0F;
    private static final int COMBINING_ENCLOSING_KEYCAP = 0x20E3;
    private static final Set<Integer> BLANK_LETTERS = Set.of(0x115F, 0x1160, 0x3164, 0xFFA0, 0x2800);

    private NameText() {}

    /** 규칙을 지키면 정리하고 앞뒤 공백을 지운 값을 돌려주고, 어기면 {@code field} 이름을 담은 {@link IllegalArgumentException}을 던진다. */
    static String require(String value, int minLength, int maxLength, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        String stripped = normalize(value).strip();
        if (stripped.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        int length = stripped.codePointCount(0, stripped.length());
        if (length < minLength || length > maxLength) {
            throw new IllegalArgumentException(field + " must be " + minLength + " to " + maxLength + " characters");
        }
        if (stripped.codePoints().anyMatch(NameText::isEmoji)) {
            throw new IllegalArgumentException(field + " must not contain emoji");
        }
        if (stripped.codePoints().anyMatch(NameText::isBreakingOrBlank)) {
            throw new IllegalArgumentException(field + " must not contain control, line separator or blank letters");
        }
        return stripped;
    }

    /** 서식 문자가 자모 사이에 끼면 NFC가 합치지 못하므로 서식 문자를 먼저 지우고 마지막에 합친다. */
    private static String normalize(String value) {
        StringBuilder cleaned = new StringBuilder(value.length());
        value.codePoints().forEach(codePoint -> {
            int type = Character.getType(codePoint);
            if (type == Character.SPACE_SEPARATOR) {
                cleaned.append(' ');
            } else if (type != Character.FORMAT) {
                cleaned.appendCodePoint(codePoint);
            }
        });
        return Normalizer.normalize(cleaned, Normalizer.Form.NFC);
    }

    private static boolean isBreakingOrBlank(int codePoint) {
        int type = Character.getType(codePoint);
        return Character.isISOControl(codePoint)
                || type == Character.LINE_SEPARATOR
                || type == Character.PARAGRAPH_SEPARATOR
                || BLANK_LETTERS.contains(codePoint);
    }

    private static boolean isEmoji(int codePoint) {
        return Character.isExtendedPictographic(codePoint)
                || Character.isEmojiPresentation(codePoint)
                || codePoint == EMOJI_VARIATION_SELECTOR
                || codePoint == COMBINING_ENCLOSING_KEYCAP;
    }
}
