package com.orbit.schedule.adapter.in.web;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.media.Schema;

/** 작업 등록. 작업명만 필수이고 나머지는 선택이다. 입력 규칙은 도메인이 한 번만 검사한다. */
public record CreateWorkRequest(
        @Schema(
                description = "작업명(필수, 최대 100자)",
                example = "배관 누수 점검 및 교체",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String name,

        @Schema(description = "작업 유형 식별자", example = "3") Long workTypeId,
        @Schema(description = "고객 정보") @Valid WorkCustomerRequest customer,
        @Schema(description = "결제 정보") @Valid WorkPaymentRequest payment,

        @Schema(description = "담당기사·일정. 모두 주면 등록과 함께 배정하고, 일부만 주면 입력한 값을 남긴 채 대기함에 두며, 비우면 대기함에 둔다") @Valid
        WorkScheduleRequest schedule) {}
