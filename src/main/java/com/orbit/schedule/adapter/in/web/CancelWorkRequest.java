package com.orbit.schedule.adapter.in.web;

import com.orbit.schedule.domain.CancellationReason;

import io.swagger.v3.oas.annotations.media.Schema;

/** 작업 취소 사유. 기타면 메모가 필수이고 다른 사유에는 메모를 받지 않는다. */
public record CancelWorkRequest(
        @Schema(
                description = "취소 사유. CUSTOMER_REQUEST(고객 요청), TECHNICIAN_ISSUE(기사 사정), "
                        + "DUPLICATE_REGISTRATION(중복 등록), OTHER(기타)",
                example = "CUSTOMER_REQUEST",
                requiredMode = Schema.RequiredMode.REQUIRED)
        CancellationReason reason,

        @Schema(description = "기타 사유 메모(OTHER일 때 필수, 최대 255자)", example = "고객 연락 두절")
        String note) {}
