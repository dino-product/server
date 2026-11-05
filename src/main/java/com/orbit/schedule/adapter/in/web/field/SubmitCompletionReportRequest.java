package com.orbit.schedule.adapter.in.web.field;

import java.util.List;

import com.orbit.schedule.domain.ActualPaymentMethod;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

/** 완료보고 제출. 배정 순번만 필수이고 나머지는 모두 비워 보낼 수 있다. 사진은 완료보고 사진 업로드 주소로 먼저 올린 파일의 식별자다. */
public record SubmitCompletionReportRequest(
        @Schema(
                description = "기사가 화면에서 본 배정의 순번(내 일정·작업 상세의 assignmentNumber). 그 뒤로 배정이 바뀌었으면 받지 않는다",
                example = "1",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Integer assignmentNumber,

        @ArraySchema(
                arraySchema =
                        @Schema(
                                description = "작업 전 사진 식별자(최대 6장, 보낸 순서대로 저장). 완료보고 사진 업로드 주소 발급"
                                        + "(completion-photo-uploads)의 fileId"),
                schema = @Schema(description = "사진 파일 식별자(UUID)", example = "0b6a1f7e-2a4c-4f6e-9d3b-1c2d3e4f5a6b"),
                maxItems = 6)
        List<String> beforePhotoIds,

        @ArraySchema(
                arraySchema =
                        @Schema(
                                description = "작업 후 사진 식별자(최대 6장, 보낸 순서대로 저장). 완료보고 사진 업로드 주소 발급"
                                        + "(completion-photo-uploads)의 fileId"),
                schema = @Schema(description = "사진 파일 식별자(UUID)", example = "4c1d2e3f-5a6b-4c7d-8e9f-0a1b2c3d4e5f"),
                maxItems = 6)
        List<String> afterPhotoIds,

        @Schema(description = "사용 부품(최대 255자)", example = "필터 1개")
        String usedParts,

        @Schema(description = "수행 메모(최대 255자)", example = "배관 누수 수리")
        String workNote,

        @Schema(description = "실제 받은 금액(원, 0 이상)", example = "150000")
        Long actualFee,

        @Schema(description = "실제 결제 방식. CASH(현금), BANK_TRANSFER(계좌이체), CREDIT_CARD(신용카드), INVOICE(청구서 발행·후불)")
        ActualPaymentMethod actualPaymentMethod) {}
