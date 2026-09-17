package com.example.user.adapter.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

public record RegisterUserRequest(
        @Schema(description = "표시 이름", example = "홍길동")
        @NotBlank(message = "필수입니다.")
        @Size(max = 100, message = "100자 이하여야 합니다.")
        String displayName) {}
