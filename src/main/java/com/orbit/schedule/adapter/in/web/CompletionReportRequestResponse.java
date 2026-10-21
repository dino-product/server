package com.orbit.schedule.adapter.in.web;

import java.time.Instant;

import com.orbit.schedule.application.port.in.command.dto.CompletionReportRequestInfo;

import io.swagger.v3.oas.annotations.media.Schema;

/** 완료보고 요청 결과. */
public record CompletionReportRequestResponse(
        @Schema(description = "작업에 남긴 요청 시각. 화면의 '요청 보냄'에 쓴다", example = "2026-10-21T02:10:00Z")
        Instant requestedAt) {

    static CompletionReportRequestResponse from(CompletionReportRequestInfo info) {
        return new CompletionReportRequestResponse(info.requestedAt());
    }
}
