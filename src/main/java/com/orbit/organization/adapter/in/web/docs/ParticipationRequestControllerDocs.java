package com.orbit.organization.adapter.in.web.docs;

import com.orbit.auth.AccountPrincipal;
import com.orbit.organization.adapter.in.web.MyParticipationRequestsResponse;
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
            summary = "내 신청 현황 조회",
            description = "요청자가 보낸 모든 참여 요청(승인 대기·승인·거절·취소)을 요청 일시순으로 돌려줍니다. 승인된 요청은 확정 유형을, "
                    + "사유를 입력해 거절된 요청은 거절 사유를 함께 보여줍니다. 다시 요청하면 이전 요청과 별개의 줄로 나옵니다.",
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MyParticipationRequestsResponse.class)))
    @ApiErrorCodes(
            enumClass = CommonErrorCode.class,
            includes = {"UNAUTHORIZED"})
    MyParticipationRequestsResponse listMine(@Parameter(hidden = true) AccountPrincipal requester);

    @Operation(
            summary = "참여 요청",
            description = "회사 코드로 확인한 발주사에 희망 유형(직원/기사)으로 승인 대기 요청을 보냅니다. 요청 경로(코드·링크·QR)는 받지 "
                    + "않습니다. 다른 발주사에는 동시에 요청할 수 있습니다. 없는 코드 ORGANIZATION-011, 이전 코드 ORGANIZATION-012, "
                    + "이미 참여 중 ORGANIZATION-013, 같은 발주사에 승인 대기 요청이 있으면 ORGANIZATION-014(새로 만들지 않음), "
                    + "비활성화된 소속이 있으면 ORGANIZATION-015, 같은 발주사의 앞선 변경이 잠금 대기 한도 안에 끝나지 않으면 "
                    + "ORGANIZATION-010입니다. 없는 코드 실패는 회사 확인과 함께 세어 한도를 넘으면 "
                    + "ORGANIZATION-018입니다.",
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
                "DEACTIVATED_MEMBER",
                "ORGANIZATION_BUSY",
                "TOO_MANY_COMPANY_CODE_ATTEMPTS"
            })
    ParticipationRequestResponse request(
            @Parameter(hidden = true) AccountPrincipal requester, RequestParticipationRequest request);

    @Operation(
            summary = "참여 요청 취소",
            description = "요청자 본인의 승인 대기 요청을 취소합니다. 취소한 요청은 총관리자의 승인 대기 목록에서 빠지며 알림을 보내지 않습니다. "
                    + "취소 뒤에는 현재 회사 코드로 다시 요청할 수 있습니다(새 요청). 없는 요청이나 다른 계정의 요청이면 ORGANIZATION-016, "
                    + "이미 승인·거절·취소된 요청이면 ORGANIZATION-017입니다.",
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponse(
            responseCode = "200",
            description = "취소 성공. 취소 상태의 요청을 돌려줍니다",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ParticipationRequestResponse.class)))
    @ApiErrorCodes(
            enumClass = CommonErrorCode.class,
            includes = {"BAD_REQUEST", "UNAUTHORIZED"})
    @ApiErrorCodes(
            enumClass = OrganizationErrorCode.class,
            includes = {"NOT_REQUESTER", "REQUEST_NOT_PENDING"})
    ParticipationRequestResponse cancel(
            @Parameter(hidden = true) AccountPrincipal requester,
            @Parameter(description = "참여 요청 식별자", example = "3", required = true) Long requestId);
}
