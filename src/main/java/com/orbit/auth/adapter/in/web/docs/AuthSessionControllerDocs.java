package com.orbit.auth.adapter.in.web.docs;

import com.orbit.auth.adapter.in.web.AuthenticatedAccountResponse;
import com.orbit.auth.adapter.in.web.security.AuthenticatedAccount;
import com.orbit.shared.error.CommonErrorCode;
import com.orbit.shared.openapi.ApiErrorCodes;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Auth session", description = "현재 Access Token의 계정과 로그아웃")
public interface AuthSessionControllerDocs {

    @Operation(
            summary = "현재 계정 조회",
            description = "Access Token이 가리키는 계정을 돌려줍니다. 토큰이 없으면 401, 폐기·만료·위조된 토큰이면 자원이 없는 것과 같은 404를 돌려줍니다.",
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AuthenticatedAccountResponse.class)))
    @ApiErrorCodes(
            enumClass = CommonErrorCode.class,
            includes = {"UNAUTHORIZED", "NOT_FOUND"})
    AuthenticatedAccountResponse me(@Parameter(hidden = true) AuthenticatedAccount account);
}
