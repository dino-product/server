package com.orbit.organization.adapter.in.web.docs;

import com.orbit.auth.AccountPrincipal;
import com.orbit.organization.adapter.in.web.WorkTypeRequest;
import com.orbit.organization.adapter.in.web.WorkTypeResponse;
import com.orbit.organization.adapter.in.web.WorkTypesResponse;
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

@Tag(name = "Work Types", description = "발주사의 작업 유형 API")
public interface WorkTypeControllerDocs {

    String BUSY = "같은 발주사의 다른 유형 변경이 끝나기를 기다리다 대기 한도를 넘기면 ORGANIZATION-010입니다(잠시 후 다시 시도).";

    @Operation(
            summary = "작업 유형 목록 조회",
            description = "삭제되지 않은 작업 유형을 만든 순서대로 조회합니다. 작업 등록·수정과 작업 목록 필터의 선택지로 씁니다. "
                    + "그 발주사의 활성 직원 소속(총관리자 포함)이면 조회할 수 있고, 직원 소속이 없으면(기사·없는 발주사 포함) ORGANIZATION-002입니다.",
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = WorkTypesResponse.class)))
    @ApiErrorCodes(
            enumClass = CommonErrorCode.class,
            includes = {"BAD_REQUEST", "UNAUTHORIZED"})
    @ApiErrorCodes(enumClass = OrganizationErrorCode.class, includes = "NOT_ORGANIZATION_MEMBER")
    WorkTypesResponse listWorkTypes(
            @Parameter(hidden = true) AccountPrincipal requester,
            @Parameter(description = "발주사 식별자", example = "1", required = true) Long organizationId);

    @Operation(
            summary = "작업 유형 생성",
            description = "이름·색상으로 작업 유형을 만듭니다. 그 발주사의 활성 총관리자만 만들 수 있습니다. 활성 소속이 아니면 ORGANIZATION-002, "
                    + "총관리자가 아닌 직원이면 ORGANIZATION-003, 이름·색상 규칙 위반은 ORGANIZATION-007, "
                    + "삭제되지 않은 작업 유형과 이름이 같으면 ORGANIZATION-009입니다. 삭제된 작업 유형과 같은 이름은 새 유형으로 만들며, "
                    + "삭제된 유형을 쓰던 작업은 새 유형으로 옮겨지지 않습니다. " + BUSY,
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponse(
            responseCode = "200",
            description = "생성 성공",
            content =
                    @Content(mediaType = "application/json", schema = @Schema(implementation = WorkTypeResponse.class)))
    @ApiErrorCodes(
            enumClass = CommonErrorCode.class,
            includes = {"BAD_REQUEST", "UNAUTHORIZED"})
    @ApiErrorCodes(
            enumClass = OrganizationErrorCode.class,
            includes = {
                "INVALID_CATEGORY_INPUT",
                "NOT_ORGANIZATION_MEMBER",
                "OWNER_ONLY",
                "DUPLICATE_CATEGORY_NAME",
                "ORGANIZATION_BUSY"
            })
    WorkTypeResponse createWorkType(
            @Parameter(hidden = true) AccountPrincipal requester,
            @Parameter(description = "발주사 식별자", example = "1", required = true) Long organizationId,
            WorkTypeRequest request);

    @Operation(
            summary = "작업 유형 수정",
            description = "이름·색상을 함께 바꿉니다. 그 발주사의 활성 총관리자만 바꿀 수 있고 규칙은 생성과 같습니다. "
                    + "작업은 유형을 식별자로 참조하므로 바뀐 이름·색상은 작업중·완료·취소 작업을 포함한 모든 작업에 보입니다. "
                    + "다른 발주사의 작업 유형·삭제된 작업 유형은 ORGANIZATION-008입니다. " + BUSY,
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponse(
            responseCode = "200",
            description = "수정 성공. 바뀐 작업 유형을 돌려줍니다",
            content =
                    @Content(mediaType = "application/json", schema = @Schema(implementation = WorkTypeResponse.class)))
    @ApiErrorCodes(
            enumClass = CommonErrorCode.class,
            includes = {"BAD_REQUEST", "UNAUTHORIZED"})
    @ApiErrorCodes(
            enumClass = OrganizationErrorCode.class,
            includes = {
                "INVALID_CATEGORY_INPUT",
                "NOT_ORGANIZATION_MEMBER",
                "OWNER_ONLY",
                "CATEGORY_NOT_FOUND",
                "DUPLICATE_CATEGORY_NAME",
                "ORGANIZATION_BUSY"
            })
    WorkTypeResponse updateWorkType(
            @Parameter(hidden = true) AccountPrincipal requester,
            @Parameter(description = "발주사 식별자", example = "1", required = true) Long organizationId,
            @Parameter(description = "작업 유형 식별자", example = "1", required = true) Long workTypeId,
            WorkTypeRequest request);
}
