package com.orbit.organization.adapter.in.web.docs;

import com.orbit.auth.AccountPrincipal;
import com.orbit.organization.adapter.in.web.PendingParticipationRequestsResponse;
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

@Tag(name = "Participation Request Approvals", description = "총관리자의 참여 요청 승인 대기·승인·거절 API")
public interface OrganizationParticipationRequestControllerDocs {

    String OWNER_ONLY = "그 발주사의 활성 총관리자만 할 수 있습니다. 직원 소속이 없으면(기사·없는 발주사 포함) ORGANIZATION-002, "
            + "총관리자가 아닌 직원이면 ORGANIZATION-003입니다.";

    @Operation(
            summary = "승인 대기 목록 조회",
            description = "승인 대기 중인 참여 요청과 건수를 요청 일시가 오래된 순으로 돌려줍니다. 승인·거절·취소된 요청은 나오지 않습니다. " + OWNER_ONLY,
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PendingParticipationRequestsResponse.class)))
    @ApiErrorCodes(
            enumClass = CommonErrorCode.class,
            includes = {"BAD_REQUEST", "UNAUTHORIZED"})
    @ApiErrorCodes(
            enumClass = OrganizationErrorCode.class,
            includes = {"NOT_ORGANIZATION_MEMBER", "OWNER_ONLY"})
    PendingParticipationRequestsResponse listPending(
            @Parameter(hidden = true) AccountPrincipal requester,
            @Parameter(description = "발주사 식별자", example = "1", required = true) Long organizationId);
}
