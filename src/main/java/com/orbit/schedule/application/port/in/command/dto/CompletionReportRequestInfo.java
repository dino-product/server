package com.orbit.schedule.application.port.in.command.dto;

import java.time.Instant;

/** 완료보고 요청 결과. 작업에 남긴 요청 시각이다. */
public record CompletionReportRequestInfo(Instant requestedAt) {}
