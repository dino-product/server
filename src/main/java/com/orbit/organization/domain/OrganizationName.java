package com.orbit.organization.domain;

/** 발주사명. 2~30자이며 앞뒤 공백·길이·이모지 규칙은 {@link NameText}를 따른다. */
public record OrganizationName(String value) {

    private static final int MIN_LENGTH = 2;
    private static final int MAX_LENGTH = 30;

    public OrganizationName {
        value = NameText.require(value, MIN_LENGTH, MAX_LENGTH, "organizationName");
    }
}
