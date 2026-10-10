package com.orbit.profile.adapter.in.web;

import io.swagger.v3.oas.annotations.media.Schema;

/** 이름·연락처 규칙은 도메인이 한 번만 검사한다(문자 수를 코드 포인트로 세고 연락처를 숫자만 남겨 판정하기 위해). */
public record SaveProfileRequest(
        @Schema(
                description = "이름. 앞뒤 공백을 지운 뒤 2~20자, 한글·영문만(숫자·특수문자·공백·이모지 불가)",
                example = "홍길동",
                minLength = 2,
                maxLength = 20,
                requiredMode = Schema.RequiredMode.REQUIRED)
        String name,

        @Schema(
                description = "휴대전화 번호. 하이픈·공백을 지운 숫자가 010으로 시작하는 11자리여야 한다. 다른 계정과 같은 번호를 허용한다",
                example = "010-1234-5678",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String phoneNumber) {}
