package com.orbit.schedule.adapter.in.web;

import java.util.List;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

/** 작업 기본정보 전체 교체. 비운 선택 항목은 지운다. 입력 규칙은 도메인이 한 번만 검사한다. */
public record UpdateWorkDetailsRequest(
        @Schema(
                description = "작업명(필수, 최대 100자)",
                example = "배관 누수 점검 및 교체",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String name,

        @Schema(description = "작업 유형 식별자. 비우면 유형 없음. 작업중에는 지금 유형을 그대로 보내야 함", example = "3")
        Long workTypeId,

        @Schema(description = "고객 정보. 비우면 모두 지움") @Valid WorkCustomerRequest customer,
        @Schema(description = "결제 정보. 비우면 모두 지움") @Valid WorkPaymentRequest payment,

        @ArraySchema(
                arraySchema = @Schema(description = "업로드를 마친 현장 사진 식별자(최대 6장, 붙인 순서). 비우면 모두 지움"),
                schema = @Schema(description = "사진 식별자(최대 500자)", example = "photo-1"),
                maxItems = 6)
        List<String> sitePhotoIds) {}
