package com.orbit.organization.adapter.in.web.docs;

import com.orbit.auth.AccountPrincipal;
import com.orbit.organization.adapter.in.web.MemberCategoriesResponse;
import com.orbit.organization.adapter.in.web.MemberCategoryRequest;
import com.orbit.organization.adapter.in.web.MemberCategoryResponse;
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

@Tag(name = "Member Categories", description = "발주사의 직원 유형·기사 유형 API")
public interface MemberCategoryControllerDocs {

    String LIST = "삭제되지 않은 유형을 만든 순서대로 조회합니다. 그 발주사의 활성 직원 소속(총관리자 포함)이면 조회할 수 있고, "
            + "직원 소속이 없으면(기사·없는 발주사 포함) ORGANIZATION-002입니다.";
    String BUSY = "같은 발주사의 다른 유형 변경이 끝나기를 기다리다 대기 한도를 넘기면 ORGANIZATION-010입니다(잠시 후 다시 시도).";
    String CREATE = "이름·색상으로 유형을 만듭니다. 그 발주사의 활성 총관리자만 만들 수 있습니다. 활성 소속이 아니면 ORGANIZATION-002, "
            + "총관리자가 아닌 직원이면 ORGANIZATION-003, 이름·색상 규칙 위반은 ORGANIZATION-007, "
            + "같은 종류의 삭제되지 않은 유형과 이름이 같으면 ORGANIZATION-009입니다. 삭제된 유형과 같은 이름은 새 유형으로 만듭니다. " + BUSY;
    String UPDATE = "이름·색상을 함께 바꿉니다. 그 발주사의 활성 총관리자만 바꿀 수 있고 규칙은 생성과 같습니다. "
            + "다른 발주사의 유형·삭제된 유형은 ORGANIZATION-008입니다. " + BUSY;
    String DELETE = "유형을 소프트 삭제합니다. 그 발주사의 활성 총관리자만 지울 수 있고 복구할 수 없습니다. 이미 삭제된 유형을 다시 지워도 성공합니다. "
            + "다른 발주사의 유형은 ORGANIZATION-008입니다. " + BUSY;

    @Operation(
            summary = "직원 유형 목록 조회",
            description = LIST,
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MemberCategoriesResponse.class)))
    @ApiErrorCodes(
            enumClass = CommonErrorCode.class,
            includes = {"BAD_REQUEST", "UNAUTHORIZED"})
    @ApiErrorCodes(enumClass = OrganizationErrorCode.class, includes = "NOT_ORGANIZATION_MEMBER")
    MemberCategoriesResponse listStaffCategories(
            @Parameter(hidden = true) AccountPrincipal requester,
            @Parameter(description = "발주사 식별자", example = "1", required = true) Long organizationId);

    @Operation(
            summary = "직원 유형 생성",
            description = CREATE,
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponse(
            responseCode = "200",
            description = "생성 성공",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MemberCategoryResponse.class)))
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
    MemberCategoryResponse createStaffCategory(
            @Parameter(hidden = true) AccountPrincipal requester,
            @Parameter(description = "발주사 식별자", example = "1", required = true) Long organizationId,
            MemberCategoryRequest request);

    @Operation(
            summary = "직원 유형 수정",
            description = UPDATE,
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponse(
            responseCode = "200",
            description = "수정 성공. 바뀐 유형을 돌려줍니다",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MemberCategoryResponse.class)))
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
    MemberCategoryResponse updateStaffCategory(
            @Parameter(hidden = true) AccountPrincipal requester,
            @Parameter(description = "발주사 식별자", example = "1", required = true) Long organizationId,
            @Parameter(description = "직원 유형 식별자", example = "1", required = true) Long categoryId,
            MemberCategoryRequest request);

    @Operation(
            summary = "직원 유형 삭제",
            description = DELETE + " 그 유형이 지정된 직원 소속은 유형 없음이 됩니다.",
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponse(responseCode = "204", description = "삭제 성공(이미 삭제된 유형 포함)")
    @ApiErrorCodes(
            enumClass = CommonErrorCode.class,
            includes = {"BAD_REQUEST", "UNAUTHORIZED"})
    @ApiErrorCodes(
            enumClass = OrganizationErrorCode.class,
            includes = {"NOT_ORGANIZATION_MEMBER", "OWNER_ONLY", "CATEGORY_NOT_FOUND", "ORGANIZATION_BUSY"})
    void deleteStaffCategory(
            @Parameter(hidden = true) AccountPrincipal requester,
            @Parameter(description = "발주사 식별자", example = "1", required = true) Long organizationId,
            @Parameter(description = "직원 유형 식별자", example = "1", required = true) Long categoryId);

    @Operation(
            summary = "기사 유형 목록 조회",
            description = LIST,
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MemberCategoriesResponse.class)))
    @ApiErrorCodes(
            enumClass = CommonErrorCode.class,
            includes = {"BAD_REQUEST", "UNAUTHORIZED"})
    @ApiErrorCodes(enumClass = OrganizationErrorCode.class, includes = "NOT_ORGANIZATION_MEMBER")
    MemberCategoriesResponse listTechnicianCategories(
            @Parameter(hidden = true) AccountPrincipal requester,
            @Parameter(description = "발주사 식별자", example = "1", required = true) Long organizationId);

    @Operation(
            summary = "기사 유형 생성",
            description = CREATE,
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponse(
            responseCode = "200",
            description = "생성 성공",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MemberCategoryResponse.class)))
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
    MemberCategoryResponse createTechnicianCategory(
            @Parameter(hidden = true) AccountPrincipal requester,
            @Parameter(description = "발주사 식별자", example = "1", required = true) Long organizationId,
            MemberCategoryRequest request);

    @Operation(
            summary = "기사 유형 수정",
            description = UPDATE,
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponse(
            responseCode = "200",
            description = "수정 성공. 바뀐 유형을 돌려줍니다",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MemberCategoryResponse.class)))
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
    MemberCategoryResponse updateTechnicianCategory(
            @Parameter(hidden = true) AccountPrincipal requester,
            @Parameter(description = "발주사 식별자", example = "1", required = true) Long organizationId,
            @Parameter(description = "기사 유형 식별자", example = "1", required = true) Long categoryId,
            MemberCategoryRequest request);

    @Operation(
            summary = "기사 유형 삭제",
            description = DELETE + " 그 유형이 지정된 기사 계약은 유형 없음이 됩니다.",
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponse(responseCode = "204", description = "삭제 성공(이미 삭제된 유형 포함)")
    @ApiErrorCodes(
            enumClass = CommonErrorCode.class,
            includes = {"BAD_REQUEST", "UNAUTHORIZED"})
    @ApiErrorCodes(
            enumClass = OrganizationErrorCode.class,
            includes = {"NOT_ORGANIZATION_MEMBER", "OWNER_ONLY", "CATEGORY_NOT_FOUND", "ORGANIZATION_BUSY"})
    void deleteTechnicianCategory(
            @Parameter(hidden = true) AccountPrincipal requester,
            @Parameter(description = "발주사 식별자", example = "1", required = true) Long organizationId,
            @Parameter(description = "기사 유형 식별자", example = "1", required = true) Long categoryId);
}
