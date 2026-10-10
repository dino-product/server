package com.orbit.organization.domain;

/**
 * 직원·기사 유형 이름. 2~10자이며 앞뒤 공백·길이·이모지 규칙은 {@link NameText}를 따른다. 같은 이름인지는 공백을 지운 값을 대소문자까지 그대로 비교한다
 * (문자 규칙·비교 방식은 기획 확정 전 잠정 — O-14).
 */
public record CategoryName(String value) {

    public static final int MIN_LENGTH = 2;
    public static final int MAX_LENGTH = 10;

    public CategoryName {
        value = NameText.require(value, MIN_LENGTH, MAX_LENGTH, "categoryName");
    }
}
