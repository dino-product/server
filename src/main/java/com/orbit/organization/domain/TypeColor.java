package com.orbit.organization.domain;

/** 직원·기사 유형의 색상 프리셋. */
public record TypeColor(int value) {
    // TODO: 디자인 확정 후 임시 번호 1~8에 실제 색상 이름·코드를 매핑한다.
    public TypeColor {
        if (value < 1 || value > 8) {
            throw new OrganizationRuleViolation("type color must be a preset from 1 to 8");
        }
    }
}
