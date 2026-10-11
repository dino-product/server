package com.orbit.organization.adapter.in.web.docs;

import com.orbit.auth.AccountPrincipal;
import com.orbit.organization.adapter.in.web.ParticipationRequestResponse;
import com.orbit.organization.adapter.in.web.RequestParticipationRequest;
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
public interface ParticipationRequestControllerDocs {

    @Operation(
            summary = "참여 요청",
            description = "회사 코드로 확인한 발주사에 희망 유형(직원/기사)으로 승인 대기 요청을 보냅니다. 요청 경로(코드·링크·QR)는 받지 "
                    + "않습니다. 다른 발주사에는 동시에 요청할 수 있습니다. 없는 코드 ORGANIZATION-011, 이전 코드 ORGANIZATION-012, "
                    + "이미 참여 중 ORGANIZATION-013, 같은 발주사에 승인 대기 요청이 있으면 ORGANIZATION-014(새로 만들지 않음), "
                    + "비활성화된 소속이 있으면 ORGANIZATION-015입니다.",
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponse(
            responseCode = "200",
            description = "요청 성공. 승인 대기 상태의 요청을 돌려줍니다",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ParticipationRequestResponse.class)))
    @ApiErrorCodes(
            enumClass = CommonErrorCode.class,
            includes = {"BAD_REQUEST", "UNAUTHORIZED"})
    @ApiErrorCodes(
            enumClass = OrganizationErrorCode.class,
            includes = {
                "COMPANY_NOT_FOUND",
                "RETIRED_COMPANY_CODE",
                "ALREADY_MEMBER",
                "PENDING_REQUEST_EXISTS",
                "DEACTIVATED_MEMBER"
            })
    ParticipationRequestResponse request(
            @Parameter(hidden = true) AccountPrincipal requester, RequestParticipationRequest request);
}
