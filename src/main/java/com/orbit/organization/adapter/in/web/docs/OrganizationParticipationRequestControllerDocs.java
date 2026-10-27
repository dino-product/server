package com.orbit.organization.adapter.in.web.docs;

import com.orbit.auth.AccountPrincipal;
import com.orbit.organization.adapter.in.web.ApproveParticipationRequestsRequest;
import com.orbit.organization.adapter.in.web.ParticipationRequestDecisionsResponse;
import com.orbit.organization.adapter.in.web.PendingParticipationRequestsResponse;
import com.orbit.organization.adapter.in.web.RejectParticipationRequestsRequest;
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
    String DECISION = "한 번에 1~100건을 보내며 같은 요청을 두 번 넣거나 목록이 비면 COMMON-400입니다. 이미 승인·거절·취소된 요청과 "
            + "다른 총관리자가 먼저 처리한 요청은 다시 처리하지 않고 그 요청만 결과에 실패(NOT_PENDING, 현재 상태 포함)로 돌려주며 "
            + "나머지는 처리합니다. 없거나 다른 발주사의 요청은 NOT_FOUND입니다. ";

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

    @Operation(
            summary = "참여 요청 승인",
            description = "고른 요청을 각각의 확정 유형으로 승인합니다(행별 승인은 한 건짜리 목록). 승인하면 확정 유형의 활성 직원 소속(총관리자 아님) "
                    + "또는 기사 계약이 바로 생기며 세부 유형은 \"유형 없음\"입니다. 신청자에게 이 발주사의 비활성 소속이 있으면 새로 만들지 않고 "
                    + "되살립니다(참여일 유지, 마지막 역할과 같은 유형이면 세부 유형 유지, 다르면 해제). 같은 발주사의 앞선 변경이 잠금 대기 "
                    + "한도 안에 끝나지 않으면 ORGANIZATION-010입니다. " + DECISION + OWNER_ONLY,
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponse(
            responseCode = "200",
            description = "처리 완료(요청별 결과 포함)",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ParticipationRequestDecisionsResponse.class)))
    @ApiErrorCodes(
            enumClass = CommonErrorCode.class,
            includes = {"BAD_REQUEST", "UNAUTHORIZED"})
    @ApiErrorCodes(
            enumClass = OrganizationErrorCode.class,
            includes = {"NOT_ORGANIZATION_MEMBER", "OWNER_ONLY", "ORGANIZATION_BUSY"})
    ParticipationRequestDecisionsResponse approve(
            @Parameter(hidden = true) AccountPrincipal requester,
            @Parameter(description = "발주사 식별자", example = "1", required = true) Long organizationId,
            ApproveParticipationRequestsRequest request);

    @Operation(
            summary = "참여 요청 거절",
            description = "고른 요청을 거절합니다(행별 거절은 한 건짜리 목록). 사유는 선택이며 입력하면 신청자에게 보입니다. 사유가 255자를 넘으면 "
                    + "ORGANIZATION-019입니다. 거절된 신청자는 다시 요청할 수 있습니다. " + DECISION + OWNER_ONLY,
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponse(
            responseCode = "200",
            description = "처리 완료(요청별 결과 포함)",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ParticipationRequestDecisionsResponse.class)))
    @ApiErrorCodes(
            enumClass = CommonErrorCode.class,
            includes = {"BAD_REQUEST", "UNAUTHORIZED"})
    @ApiErrorCodes(
            enumClass = OrganizationErrorCode.class,
            includes = {"INVALID_REJECT_REASON", "NOT_ORGANIZATION_MEMBER", "OWNER_ONLY"})
    ParticipationRequestDecisionsResponse reject(
            @Parameter(hidden = true) AccountPrincipal requester,
            @Parameter(description = "발주사 식별자", example = "1", required = true) Long organizationId,
            RejectParticipationRequestsRequest request);
}
