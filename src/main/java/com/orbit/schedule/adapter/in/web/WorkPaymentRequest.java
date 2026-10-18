package com.orbit.schedule.adapter.in.web;

import com.orbit.schedule.domain.PaymentMethod;

import io.swagger.v3.oas.annotations.media.Schema;

/** 작업 등록·기본정보 수정의 결제 정보. 모든 항목이 선택이다. */
public record WorkPaymentRequest(
        @Schema(description = "작업 금액(원, 0 이상)", example = "120000")
        Long fee,

        @Schema(
                description =
                        "결제 방식. ON_SITE_CARD(현장 카드), ON_SITE_CASH(현장 현금), BANK_TRANSFER(계좌이체), INVOICE(청구서 발행·후불)",
                example = "ON_SITE_CARD")
        PaymentMethod method) {}
