package com.orbit.organization.adapter.in.web;

import io.swagger.v3.oas.annotations.media.Schema;

/** 유형 생성·수정 본문. 이름·색상 규칙은 도메인이 한 번만 검사한다(문자 수를 코드 포인트로 세기 위해). */
public record MemberCategoryRequest(
        @Schema(
                description = "유형 이름. 앞뒤 공백을 지운 뒤 2~10자, 이모지·제어·서식 문자(줄바꿈·탭·제로폭 문자 등) 불가. "
                        + "같은 발주사·같은 종류의 삭제되지 않은 유형과 같은 이름은 쓸 수 없다",
                example = "전기 기사",
                minLength = 2,
                maxLength = 10,
                requiredMode = Schema.RequiredMode.REQUIRED)
        String name,

        @Schema(
                description = "색상. 프리셋 팔레트 #EF4444, #F97316, #EAB308, #22C55E, #14B8A6, #3B82F6, #8B5CF6, #EC4899 중 하나"
                        + "(대소문자 무시, 팔레트 값은 기획 확정 전 잠정)",
                example = "#3B82F6",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String color) {}
