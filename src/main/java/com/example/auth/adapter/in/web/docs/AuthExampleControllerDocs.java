package com.example.auth.adapter.in.web.docs;

import jakarta.validation.constraints.Positive;

import com.example.auth.adapter.in.web.AuthSubjectResponse;
import com.example.auth.application.error.AuthErrorCode;
import com.example.shared.error.CommonErrorCode;
import com.example.shared.openapi.ApiErrorCodes;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Auth examples", description = "인증 모듈 구조 예제")
public interface AuthExampleControllerDocs {
    @Operation(summary = "예제 subject 조회", description = "사용자 공개 계약을 auth의 subject로 변환합니다. 실제 인증이나 토큰 발급을 수행하지 않습니다.")
    @ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AuthSubjectResponse.class)))
    @ApiErrorCodes(enumClass = CommonErrorCode.class, includes = "BAD_REQUEST")
    @ApiErrorCodes(enumClass = AuthErrorCode.class, includes = "USER_NOT_FOUND")
    AuthSubjectResponse getSubject(
            @Parameter(description = "사용자 식별자", example = "1", required = true) @Positive Long userId);
}
