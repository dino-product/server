package com.orbit.organization.adapter.in.web;

import com.orbit.organization.domain.Industry;

import io.swagger.v3.oas.annotations.media.Schema;

/** 발주사명 규칙(앞뒤 공백 제거, 길이, 이모지 불가)은 도메인이 한 번만 검사한다(문자 수를 코드 포인트로 세기 위해). */
public record CreateOrganizationRequest(
        @Schema(
                description = "발주사명. 앞뒤 공백을 지운 뒤 2~30자, 이모지 불가",
                example = "오르빗 설비",
                minLength = 2,
                maxLength = 30,
                requiredMode = Schema.RequiredMode.REQUIRED)
        String name,

        @Schema(
                description = "업종. HVAC(냉난방 설비), ELECTRICAL_ELECTRONICS(전기·전자), PLUMBING(배관·설비), "
                        + "APPLIANCE_SERVICE(가전 A/S), FACILITY_MANAGEMENT(종합 시설관리), OTHER(기타)",
                example = "HVAC",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Industry industry) {}
