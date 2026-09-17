package com.example.user.adapter.in.web.docs;

import com.example.shared.error.CommonErrorCode;
import com.example.shared.openapi.ApiErrorCodes;
import com.example.user.adapter.in.web.RegisterUserRequest;
import com.example.user.adapter.in.web.UserResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Users", description = "사용자 API")
public interface UserControllerDocs {

    @Operation(summary = "사용자 등록", description = "표시 이름으로 사용자를 등록합니다.")
    @ApiResponse(
            responseCode = "200",
            description = "등록 성공",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserResponse.class)))
    @ApiErrorCodes(enumClass = CommonErrorCode.class, includes = "BAD_REQUEST")
    UserResponse register(RegisterUserRequest request);
}
