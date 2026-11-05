package com.orbit.schedule.adapter.in.web.field;

import io.swagger.v3.oas.annotations.media.Schema;

/** 기사가 화면에서 본 배정을 가리키는 요청. 수락처럼 입력 항목 없이 배정만 확인하는 기사 요청이 함께 쓴다. */
public record AssignmentNumberRequest(
        @Schema(
                description = "기사가 화면에서 본 배정의 순번(내 일정·작업 상세의 assignmentNumber). 그 뒤로 배정이 바뀌었으면 처리하지 않는다",
                example = "1",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Integer assignmentNumber) {}
