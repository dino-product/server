package com.example.user.adapter.in.web;

import com.example.user.application.port.in.command.dto.RegisteredUserInfo;

import io.swagger.v3.oas.annotations.media.Schema;

public record UserResponse(
        @Schema(description = "사용자 식별자", example = "1") Long id,

        @Schema(description = "표시 이름", example = "홍길동") String displayName) {

    static UserResponse from(RegisteredUserInfo user) {
        return new UserResponse(user.id(), user.displayName());
    }
}
