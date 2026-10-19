package com.orbit.schedule.adapter.in.web;

import io.swagger.v3.oas.annotations.media.Schema;

/** 작업 등록·기본정보 수정의 고객 정보. 모든 항목이 선택이며 길이 규칙은 도메인이 한 번만 검사한다. */
public record WorkCustomerRequest(
        @Schema(description = "고객명(최대 50자)", example = "이정호")
        String name,

        @Schema(description = "고객 연락처(최대 20자)", example = "010-1234-5678")
        String phone,

        @Schema(description = "현장 우편번호(숫자 5자리)", example = "04043")
        String postalCode,

        @Schema(description = "현장 기본주소(최대 200자)", example = "서울시 마포구 양화로 45")
        String address,

        @Schema(description = "현장 상세주소(최대 100자). 기본주소 없이 보내면 SCHEDULE-003", example = "3층 302호")
        String addressDetail) {}
