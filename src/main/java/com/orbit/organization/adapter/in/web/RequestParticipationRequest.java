package com.orbit.organization.adapter.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import com.orbit.organization.domain.ParticipationType;

import io.swagger.v3.oas.annotations.media.Schema;

/** 회사 코드의 정규화·형식 확인은 서비스가 회사 확인과 같은 규칙으로 한다. */
public record RequestParticipationRequest(
        @Schema(
                description = "사용자가 입력한 회사 코드. 링크·QR로 들어온 경우 그 코드. 대소문자 무시, 공백·하이픈 무시",
                example = "7K2M9X",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        String companyCode,

        @Schema(
                description = "희망 유형. STAFF 직원, TECHNICIAN 기사. 최종 유형은 총관리자가 승인할 때 정한다",
                example = "TECHNICIAN",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        ParticipationType desiredType) {}
