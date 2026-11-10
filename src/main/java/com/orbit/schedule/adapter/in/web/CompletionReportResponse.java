package com.orbit.schedule.adapter.in.web;

import java.util.List;

import com.orbit.schedule.application.port.in.query.dto.CompletionReportInfo;
import com.orbit.schedule.domain.ActualPaymentMethod;

import io.swagger.v3.oas.annotations.media.Schema;

/** 완료보고 내용. 작업 상세와 완료보고 조회가 함께 쓴다. */
@Schema(description = "완료보고")
public record CompletionReportResponse(
        @Schema(description = "작업 전 사진. 보고의 순서 그대로") List<PhotoResponse> beforePhotos,
        @Schema(description = "작업 후 사진. 보고의 순서 그대로") List<PhotoResponse> afterPhotos,
        @Schema(description = "사용 부품") String usedParts,
        @Schema(description = "수행 메모") String workNote,
        @Schema(description = "실제 결제 금액(원)") Long actualFee,

        @Schema(description = "실제 결제 방식. CASH(현금), BANK_TRANSFER(계좌이체), CREDIT_CARD(신용카드), INVOICE(청구서 발행·후불)")
        ActualPaymentMethod actualPaymentMethod) {

    public static CompletionReportResponse from(CompletionReportInfo info) {
        return new CompletionReportResponse(
                info.beforePhotos().stream().map(PhotoResponse::from).toList(),
                info.afterPhotos().stream().map(PhotoResponse::from).toList(),
                info.usedParts(),
                info.workNote(),
                info.actualFee(),
                info.actualPaymentMethod());
    }
}
