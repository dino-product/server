package com.example.auth.adapter.in.web;

import com.example.auth.application.port.in.query.dto.AuthSubjectInfo;

import io.swagger.v3.oas.annotations.media.Schema;

public record AuthSubjectResponse(
        @Schema(description = "예제 subject 식별자", example = "user:1")
        String subject) {
    static AuthSubjectResponse from(AuthSubjectInfo info) {
        return new AuthSubjectResponse(info.subject());
    }
}
