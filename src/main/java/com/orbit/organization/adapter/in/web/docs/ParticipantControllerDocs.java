package com.orbit.organization.adapter.in.web.docs;

import com.orbit.auth.AccountPrincipal;
import com.orbit.organization.adapter.in.web.ParticipantsResponse;
import com.orbit.organization.application.error.OrganizationErrorCode;
import com.orbit.organization.domain.ParticipationType;
import com.orbit.shared.error.CommonErrorCode;
import com.orbit.shared.openapi.ApiErrorCodes;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Participants", description = "총관리자의 참여 인원(직원 소속·기사 계약) 조회·관리 API")
public interface ParticipantControllerDocs {

    String OWNER_ONLY = "그 발주사의 활성 총관리자만 할 수 있습니다. 직원 소속이 없으면(기사·없는 발주사 포함) ORGANIZATION-002, "
            + "총관리자가 아닌 직원이면 ORGANIZATION-003입니다.";

    @Operation(
            summary = "참여 인원 목록 조회",
            description = "직원 소속(총관리자 포함)과 기사 계약을 계정마다 한 줄로 이름·역할·세부 유형·상태·참여일과 함께 돌려줍니다. 비활성 구성원도 "
                    + "나오며, 역할을 바꾼 계정은 지금 역할(모두 비활성이면 마지막 역할)의 줄만 나옵니다. " + OWNER_ONLY,
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ParticipantsResponse.class)))
    @ApiErrorCodes(
            enumClass = CommonErrorCode.class,
            includes = {"BAD_REQUEST", "UNAUTHORIZED"})
    @ApiErrorCodes(
            enumClass = OrganizationErrorCode.class,
            includes = {"NOT_ORGANIZATION_MEMBER", "OWNER_ONLY"})
    ParticipantsResponse listParticipants(
            @Parameter(hidden = true) AccountPrincipal requester,
            @Parameter(description = "발주사 식별자", example = "1", required = true) Long organizationId,
            @Parameter(description = "역할 필터. 없으면 전체, STAFF 사무실 직원(총관리자 포함), TECHNICIAN 기사", example = "TECHNICIAN")
                    ParticipationType type,
            @Parameter(description = "이름 검색어. 앞뒤 공백을 지우고 대소문자 구분 없이 이름에 들어 있으면 나옵니다. 비우면 거르지 않습니다", example = "홍")
                    String name);
}
