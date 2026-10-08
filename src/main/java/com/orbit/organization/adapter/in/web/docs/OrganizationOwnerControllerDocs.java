package com.orbit.organization.adapter.in.web.docs;

import jakarta.validation.constraints.Positive;

import com.orbit.auth.AccountPrincipal;
import com.orbit.organization.application.error.OrganizationErrorCode;
import com.orbit.shared.error.CommonErrorCode;
import com.orbit.shared.openapi.ApiErrorCodes;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Organization owners", description = "총관리자 지정·해제 API")
public interface OrganizationOwnerControllerDocs {

    @Operation(
            summary = "총관리자 지정",
            description = "총관리자가 같은 발주사의 직원 소속을 총관리자로 추가 지정합니다. 기존 총관리자는 그대로 유지되며 바로 반영됩니다. "
                    + "이미 총관리자인 소속을 다시 지정해도 성공합니다. 요청자가 그 발주사 소속이 아니면 ORGANIZATION-002, "
                    + "총관리자가 아니면 ORGANIZATION-003, 대상 직원 소속이 없거나 다른 발주사 소속이면 ORGANIZATION-004입니다.",
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponse(responseCode = "204", description = "지정 성공")
    @ApiErrorCodes(
            enumClass = CommonErrorCode.class,
            includes = {"BAD_REQUEST", "UNAUTHORIZED", "NOT_FOUND"})
    @ApiErrorCodes(
            enumClass = OrganizationErrorCode.class,
            includes = {"NOT_ORGANIZATION_MEMBER", "OWNER_ONLY", "MEMBERSHIP_NOT_FOUND"})
    void designate(
            @Parameter(hidden = true) AccountPrincipal requester,
            @Parameter(description = "발주사 식별자", example = "1", required = true) @Positive Long organizationId,
            @Parameter(description = "총관리자로 지정할 직원 소속 식별자", example = "2", required = true) @Positive
                    Long membershipId);
}
