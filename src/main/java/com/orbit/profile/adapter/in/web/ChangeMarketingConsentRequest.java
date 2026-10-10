package com.orbit.profile.adapter.in.web;

import io.swagger.v3.oas.annotations.media.Schema;

/** 값이 비면 입력 오류(COMMON-400)다. 컨트롤러가 메서드 검증을 켜 두어 본문 제약 대신 서비스가 검사한다. */
public record ChangeMarketingConsentRequest(
        @Schema(description = "마케팅 정보 수신 동의 여부", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
        Boolean agreed) {}
