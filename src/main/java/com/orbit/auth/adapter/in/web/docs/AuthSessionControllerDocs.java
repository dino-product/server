package com.orbit.auth.adapter.in.web.docs;

import com.orbit.auth.adapter.in.web.AuthenticatedAccountResponse;
import com.orbit.auth.adapter.in.web.security.AuthenticatedAccount;
import com.orbit.auth.application.error.AuthErrorCode;
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
            description = "Access Token이 가리키는 계정을 저장소에서 확인해 돌려줍니다. 토큰이 없으면 401, "
                    + "폐기·만료·위조된 토큰이면 자원이 없는 것과 같은 404, 계정이 삭제됐으면 AUTH-004를 돌려줍니다.",
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
    @ApiErrorCodes(enumClass = AuthErrorCode.class, includes = "ACCOUNT_NOT_FOUND")
    AuthenticatedAccountResponse me(@Parameter(hidden = true) AuthenticatedAccount account);

    @Operation(
            summary = "로그아웃",
            description = "현재 Access Token을 만료까지 폐기합니다. 이후 같은 토큰으로 보호 자원에 접근하면 자원이 없는 것과 같은 404를 돌려줍니다.",
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponse(responseCode = "204", description = "로그아웃 성공")
    @ApiErrorCodes(
            enumClass = CommonErrorCode.class,
            includes = {"UNAUTHORIZED", "NOT_FOUND"})
    void logout(@Parameter(hidden = true) AuthenticatedAccount account);
}
