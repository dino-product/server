package com.orbit.organization.adapter.in.web.docs;

import com.orbit.auth.AccountPrincipal;
import com.orbit.organization.adapter.in.web.CompanyCodeResponse;
import com.orbit.organization.application.error.OrganizationErrorCode;
import com.orbit.shared.error.CommonErrorCode;
import com.orbit.shared.openapi.ApiErrorCodes;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Organizations", description = "발주사 API")
public interface CompanyCodeControllerDocs {

    @Operation(
            summary = "회사 코드 조회",
            description = "참여 링크·QR의 근거인 현재 회사 코드를 조회합니다. 그 발주사의 활성 총관리자만 조회할 수 있습니다. "
                    + "활성 소속이 아니면(없는 발주사 포함) ORGANIZATION-002, 총관리자가 아닌 직원이면 ORGANIZATION-003입니다.",
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CompanyCodeResponse.class)))
    @ApiErrorCodes(
            enumClass = CommonErrorCode.class,
            includes = {"BAD_REQUEST", "UNAUTHORIZED"})
    @ApiErrorCodes(
            enumClass = OrganizationErrorCode.class,
            includes = {"NOT_ORGANIZATION_MEMBER", "OWNER_ONLY"})
    CompanyCodeResponse get(
            @Parameter(hidden = true) AccountPrincipal requester,
            @Parameter(description = "발주사 식별자", example = "1", required = true) Long organizationId);

    @Operation(
            summary = "회사 코드 변경",
            description = "서버가 무작위로 새 회사 코드를 발급합니다(본문 없음). 이전 코드는 폐기되어 어느 발주사에도 다시 발급하지 않으며, "
                    + "이전 코드로 만든 링크·QR로는 새 참여 요청을 할 수 없습니다. 기존 소속은 그대로 유지됩니다. "
                    + "그 발주사의 활성 총관리자만 바꿀 수 있습니다. 활성 소속이 아니면(없는 발주사 포함) ORGANIZATION-002, "
                    + "총관리자가 아닌 직원이면 ORGANIZATION-003입니다.",
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponse(
            responseCode = "200",
            description = "변경 성공. 새 회사 코드를 돌려줍니다",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CompanyCodeResponse.class)))
    @ApiErrorCodes(
            enumClass = CommonErrorCode.class,
            includes = {"BAD_REQUEST", "UNAUTHORIZED"})
    @ApiErrorCodes(
            enumClass = OrganizationErrorCode.class,
            includes = {"NOT_ORGANIZATION_MEMBER", "OWNER_ONLY"})
    CompanyCodeResponse change(
            @Parameter(hidden = true) AccountPrincipal requester,
            @Parameter(description = "발주사 식별자", example = "1", required = true) Long organizationId);
}
