package com.orbit.profile.adapter.in.web;

import io.swagger.v3.oas.annotations.media.Schema;

/** 필수 약관 값이 비면 동의하지 않은 것으로 본다. 마케팅 값이 비면 처음 가입은 미동의, 재동의는 기존 값을 그대로 둔다. */
public record AgreeToTermsRequest(
        @Schema(description = "[필수] 서비스 이용약관 동의", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
        Boolean serviceTerms,

        @Schema(description = "[필수] 개인정보 처리방침 동의", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
        Boolean privacyPolicy,

        @Schema(description = "[선택] 마케팅 정보 수신 동의. 보내지 않으면 바꾸지 않는다", example = "false")
        Boolean marketing) {}
