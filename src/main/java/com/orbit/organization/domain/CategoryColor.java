package com.orbit.organization.domain;

import java.util.List;
import java.util.Locale;

/** 유형 색상. 프리셋 팔레트 8색 중 하나를 대문자 {@code #RRGGBB}로 둔다. 팔레트 값은 기획·디자인 확정 전 잠정값이다(O-10). */
public record CategoryColor(String value) {

    /** 표시 순서대로의 프리셋 팔레트. */
    public static final List<String> PRESETS =
            List.of("#EF4444", "#F97316", "#EAB308", "#22C55E", "#14B8A6", "#3B82F6", "#8B5CF6", "#EC4899");

    public static final int LENGTH = 7;

    public CategoryColor {
        if (value == null) {
            throw new IllegalArgumentException("categoryColor must not be null");
        }
        value = value.toUpperCase(Locale.ROOT);
        if (!PRESETS.contains(value)) {
            throw new IllegalArgumentException("categoryColor must be one of the preset palette");
        }
    }
}
