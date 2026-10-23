package com.orbit.auth.adapter.in.web.docs;

import com.orbit.auth.adapter.in.web.AuthenticatedAccountResponse;
import com.orbit.auth.adapter.in.web.RefreshTokensRequest;
import com.orbit.auth.adapter.in.web.RefreshedTokensResponse;
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

@Tag(name = "Auth session", description = "현재 Access Token의 계정, 토큰 재발급과 로그아웃")
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
            description = "현재 Access Token을 만료까지 폐기하고 이 기기의 refresh token을 끝냅니다. 이후 같은 토큰으로 보호 자원에 접근하면 자원이 없는 것과"
                    + " 같은 404를, 이 기기의 refresh token으로 재발급하면 AUTH-009를 돌려줍니다. 같은 계정의 다른 기기 로그인은 유지됩니다.",
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponse(responseCode = "204", description = "로그아웃 성공")
    @ApiErrorCodes(
            enumClass = CommonErrorCode.class,
            includes = {"UNAUTHORIZED", "NOT_FOUND"})
    void logout(@Parameter(hidden = true) AuthenticatedAccount account);

    @Operation(
            summary = "토큰 재발급",
            description = "refresh token으로 같은 기기 세션의 새 Access Token과 다음 refresh token을 발급합니다. 보낸 refresh token은 더 쓸 수"
                    + " 없으니 응답의 값으로 바꿔 보관합니다. 같은 refresh token으로 거의 동시에(30초 안) 다시 보내면 같은 다음 refresh token을"
                    + " 돌려줍니다. 그 뒤에 이미 쓴 refresh token이 오면 탈취로 보고 그 기기 세션을 끝내 최신 refresh token도 쓸 수 없게 됩니다."
                    + " 형식 오류·만료·로그아웃·재사용을 구분하지 않고 AUTH-009를 돌려주며, 이때는 다시 로그인합니다. 만료된 Access Token이"
                    + " 함께 와도 검사하지 않습니다.")
    @ApiResponse(
            responseCode = "200",
            description = "재발급 성공",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = RefreshedTokensResponse.class)))
    @ApiErrorCodes(enumClass = CommonErrorCode.class, includes = "BAD_REQUEST")
    @ApiErrorCodes(enumClass = AuthErrorCode.class, includes = "INVALID_REFRESH_TOKEN")
    RefreshedTokensResponse refresh(RefreshTokensRequest request);
}
