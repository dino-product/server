package com.orbit.organization.adapter.in.web.docs;

import com.orbit.auth.AccountPrincipal;
import com.orbit.organization.adapter.in.web.CreateOrganizationRequest;
import com.orbit.organization.adapter.in.web.CreatedOrganizationResponse;
import com.orbit.organization.adapter.in.web.OrganizationResponse;
import com.orbit.organization.adapter.in.web.UpdateOrganizationRequest;
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
public interface OrganizationControllerDocs {

    @Operation(
            summary = "발주사 생성",
            description = "발주사명·업종으로 발주사를 만듭니다. 요청자의 총관리자 직원 소속과 회사 코드가 함께 생깁니다. "
                    + "이미 다른 발주사에 소속된 계정도 만들 수 있습니다. 발주사명·업종 규칙 위반은 ORGANIZATION-001, "
                    + "본문 형식 오류(목록에 없는 업종 값 등)는 COMMON-400입니다.",
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponse(
            responseCode = "200",
            description = "생성 성공",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CreatedOrganizationResponse.class)))
    @ApiErrorCodes(
            enumClass = CommonErrorCode.class,
            includes = {"BAD_REQUEST", "UNAUTHORIZED", "NOT_FOUND"})
    @ApiErrorCodes(enumClass = OrganizationErrorCode.class, includes = "INVALID_ORGANIZATION_INPUT")
    CreatedOrganizationResponse create(
            @Parameter(hidden = true) AccountPrincipal requester, CreateOrganizationRequest request);

    @Operation(
            summary = "발주사 정보 조회",
            description = "조직 설정의 발주사명·업종을 조회합니다. 그 발주사의 활성 총관리자만 조회할 수 있습니다. "
                    + "활성 소속이 아니면(없는 발주사 포함) ORGANIZATION-002, 총관리자가 아닌 직원이면 ORGANIZATION-003입니다. "
                    + "회사 코드는 포함하지 않습니다.",
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = OrganizationResponse.class)))
    @ApiErrorCodes(
            enumClass = CommonErrorCode.class,
            includes = {"BAD_REQUEST", "UNAUTHORIZED"})
    @ApiErrorCodes(
            enumClass = OrganizationErrorCode.class,
            includes = {"NOT_ORGANIZATION_MEMBER", "OWNER_ONLY"})
    OrganizationResponse get(
            @Parameter(hidden = true) AccountPrincipal requester,
            @Parameter(description = "발주사 식별자", example = "1", required = true) Long organizationId);

    @Operation(
            summary = "발주사 정보 수정",
            description = "발주사명·업종을 함께 바꿉니다. 그 발주사의 활성 총관리자만 수정할 수 있고 검증은 발주사 생성과 같습니다. "
                    + "다른 발주사와 같은 이름도 허용합니다. 활성 소속이 아니면(없는 발주사 포함) ORGANIZATION-002, "
                    + "총관리자가 아닌 직원이면 ORGANIZATION-003, 발주사명·업종 규칙 위반은 ORGANIZATION-001, "
                    + "본문 형식 오류(목록에 없는 업종 값 등)는 COMMON-400입니다.",
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponse(
            responseCode = "200",
            description = "수정 성공. 바뀐 발주사 정보를 돌려줍니다",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = OrganizationResponse.class)))
    @ApiErrorCodes(
            enumClass = CommonErrorCode.class,
            includes = {"BAD_REQUEST", "UNAUTHORIZED"})
    @ApiErrorCodes(
            enumClass = OrganizationErrorCode.class,
            includes = {"INVALID_ORGANIZATION_INPUT", "NOT_ORGANIZATION_MEMBER", "OWNER_ONLY"})
    OrganizationResponse update(
            @Parameter(hidden = true) AccountPrincipal requester,
            @Parameter(description = "발주사 식별자", example = "1", required = true) Long organizationId,
            UpdateOrganizationRequest request);
}
