package com.orbit.organization.adapter.in.web.docs;

import com.orbit.auth.AccountPrincipal;
import com.orbit.organization.adapter.in.web.CompanyCheckResponse;
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

@Tag(name = "Participation Requests", description = "참여 요청 API")
public interface CompanyCheckControllerDocs {

    @Operation(
            summary = "회사 확인",
            description = "회사 코드로 발주사를 찾아 발주사명과 요청자가 지금 참여 요청을 보낼 수 있는지 돌려줍니다. 참여 링크·QR도 회사 코드로 "
                    + "확인합니다. 입력은 대소문자를 무시하고 공백·하이픈을 지웁니다. 소속이 없어도 인증된 계정이면 확인할 수 있습니다. "
                    + "없는 코드(형식이 틀린 입력 포함)는 ORGANIZATION-011, 회사 코드 변경으로 폐기된 이전 코드는 ORGANIZATION-012입니다.",
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponse(
            responseCode = "200",
            description = "확인 성공",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CompanyCheckResponse.class)))
    @ApiErrorCodes(
            enumClass = CommonErrorCode.class,
            includes = {"BAD_REQUEST", "UNAUTHORIZED"})
    @ApiErrorCodes(
            enumClass = OrganizationErrorCode.class,
            includes = {"COMPANY_NOT_FOUND", "RETIRED_COMPANY_CODE"})
    CompanyCheckResponse check(
            @Parameter(hidden = true) AccountPrincipal requester,
            @Parameter(description = "사용자가 입력한 회사 코드. 대소문자 무시, 공백·하이픈 무시", example = "7k2m-9x", required = true)
                    String companyCode);
}
