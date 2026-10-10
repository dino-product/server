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
}
