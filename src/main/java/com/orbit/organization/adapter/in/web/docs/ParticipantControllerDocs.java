package com.orbit.organization.adapter.in.web.docs;

import com.orbit.auth.AccountPrincipal;
import com.orbit.organization.adapter.in.web.MemberCategoryAssignmentRequest;
import com.orbit.organization.adapter.in.web.ParticipantRoleChangeRequest;
import com.orbit.organization.adapter.in.web.ParticipantRoleChangeResponse;
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
    String BUSY = "같은 발주사의 다른 변경이 끝나기를 기다리다 대기 한도를 넘기면 ORGANIZATION-010입니다(잠시 후 다시 시도).";
    String STAFF_TARGET = "대상 직원 소속이 없거나 다른 발주사 소속이면 ORGANIZATION-004입니다. ";
    String TECHNICIAN_TARGET = "대상 기사 계약이 없거나 다른 발주사 계약이면 ORGANIZATION-020입니다. ";
    String REACTIVATE = "비활성 구성원을 다시 활성으로 바꿉니다. 참여일·세부 유형은 그대로이고, 이미 활성이면 아무것도 바꾸지 않고 성공합니다. "
            + "같은 계정이 이 발주사에서 다른 역할로 활성이면 ORGANIZATION-022, 역할이 바뀌기 전 행이라 목록에 보이지 않으면 ORGANIZATION-023입니다. ";
    String ASSIGN = "세부 유형을 지정하거나 categoryId를 비워 \"유형 없음\"으로 바꿉니다. 같은 유형을 다시 지정해도 성공합니다. "
            + "유형이 없거나 삭제됐거나 다른 발주사의 유형이면 ORGANIZATION-008, 대상이 비활성이면 ORGANIZATION-021입니다. " + BUSY + " "
            + OWNER_ONLY;

    String ROLE_CHANGE = "역할을 바꾸면 지금 행은 비활성으로 끝나고, 바뀐 역할의 행이 없으면 참여일을 이어받아 새로 생기며 있으면 다시 활성이 됩니다"
            + "(기사 계약은 같은 식별자로 돌아와 배정·작업 이력이 이어짐). 바뀐 역할의 세부 유형은 categoryId로 고르고, 비우면 \"유형 없음\"입니다. "
            + "유형이 없거나 삭제됐거나 다른 발주사·다른 종류의 유형이면 ORGANIZATION-008, 대상이 비활성이면 ORGANIZATION-026입니다. ";

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

    @Operation(
            summary = "직원 유형 지정",
            description =
                    "직원 소속(총관리자 포함)에 그 발주사의 직원 유형을 지정합니다. 대상 직원 소속이 없거나 다른 발주사 소속이면 ORGANIZATION-004입니다. " + ASSIGN,
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponse(responseCode = "204", description = "지정 성공")
    @ApiErrorCodes(
            enumClass = CommonErrorCode.class,
            includes = {"BAD_REQUEST", "UNAUTHORIZED"})
    @ApiErrorCodes(
            enumClass = OrganizationErrorCode.class,
            includes = {
                "NOT_ORGANIZATION_MEMBER",
                "OWNER_ONLY",
                "MEMBERSHIP_NOT_FOUND",
                "CATEGORY_NOT_FOUND",
                "INACTIVE_MEMBER",
                "ORGANIZATION_BUSY"
            })
    void assignStaffCategory(
            @Parameter(hidden = true) AccountPrincipal requester,
            @Parameter(description = "발주사 식별자", example = "1", required = true) Long organizationId,
            @Parameter(description = "직원 소속 식별자(참여 인원 목록의 memberId)", example = "3", required = true) Long membershipId,
            MemberCategoryAssignmentRequest request);

    @Operation(
            summary = "기사 유형 지정",
            description = "기사 계약에 그 발주사의 기사 유형을 지정합니다. 대상 기사 계약이 없거나 다른 발주사 계약이면 ORGANIZATION-020입니다. " + ASSIGN,
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponse(responseCode = "204", description = "지정 성공")
    @ApiErrorCodes(
            enumClass = CommonErrorCode.class,
            includes = {"BAD_REQUEST", "UNAUTHORIZED"})
    @ApiErrorCodes(
            enumClass = OrganizationErrorCode.class,
            includes = {
                "NOT_ORGANIZATION_MEMBER",
                "OWNER_ONLY",
                "TECHNICIAN_NOT_FOUND",
                "CATEGORY_NOT_FOUND",
                "INACTIVE_MEMBER",
                "ORGANIZATION_BUSY"
            })
    void assignTechnicianCategory(
            @Parameter(hidden = true) AccountPrincipal requester,
            @Parameter(description = "발주사 식별자", example = "1", required = true) Long organizationId,
            @Parameter(description = "기사 계약 식별자(참여 인원 목록의 memberId)", example = "5", required = true) Long technicianId,
            MemberCategoryAssignmentRequest request);

    @Operation(
            summary = "직원 소속 비활성화",
            description = "직원 소속(총관리자 포함)을 비활성화합니다. 소속을 지우지 않고 상태만 바꾸며, 비활성 소속은 이 발주사 기능을 쓸 수 없습니다. "
                    + "총관리자 소속은 총관리자 표시를 떼므로 다시 활성화하면 직원입니다. 본인도 비활성화할 수 있지만 활성 총관리자가 1명뿐이면 "
                    + "ORGANIZATION-005입니다. 이미 비활성이면 아무것도 바꾸지 않고 성공합니다. "
                    + STAFF_TARGET
                    + BUSY
                    + " "
                    + OWNER_ONLY,
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponse(responseCode = "204", description = "비활성화 성공")
    @ApiErrorCodes(
            enumClass = CommonErrorCode.class,
            includes = {"BAD_REQUEST", "UNAUTHORIZED"})
    @ApiErrorCodes(
            enumClass = OrganizationErrorCode.class,
            includes = {
                "NOT_ORGANIZATION_MEMBER",
                "OWNER_ONLY",
                "MEMBERSHIP_NOT_FOUND",
                "LAST_OWNER",
                "ORGANIZATION_BUSY"
            })
    void deactivateStaff(
            @Parameter(hidden = true) AccountPrincipal requester,
            @Parameter(description = "발주사 식별자", example = "1", required = true) Long organizationId,
            @Parameter(description = "직원 소속 식별자(참여 인원 목록의 memberId)", example = "3", required = true)
                    Long membershipId);

    @Operation(
            summary = "직원 소속 재활성화",
            description = REACTIVATE + STAFF_TARGET + BUSY + " " + OWNER_ONLY,
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponse(responseCode = "204", description = "재활성화 성공")
    @ApiErrorCodes(
            enumClass = CommonErrorCode.class,
            includes = {"BAD_REQUEST", "UNAUTHORIZED"})
    @ApiErrorCodes(
            enumClass = OrganizationErrorCode.class,
            includes = {
                "NOT_ORGANIZATION_MEMBER",
                "OWNER_ONLY",
                "MEMBERSHIP_NOT_FOUND",
                "ACTIVE_IN_OTHER_ROLE",
                "NOT_LAST_ROLE",
                "ORGANIZATION_BUSY"
            })
    void reactivateStaff(
            @Parameter(hidden = true) AccountPrincipal requester,
            @Parameter(description = "발주사 식별자", example = "1", required = true) Long organizationId,
            @Parameter(description = "직원 소속 식별자(참여 인원 목록의 memberId)", example = "3", required = true)
                    Long membershipId);

    @Operation(
            summary = "기사 계약 비활성화",
            description = "기사 계약을 비활성화합니다. 계약을 지우지 않고 상태만 바꾸며, 같은 요청에서 그 기사의 배정됨·작업전 작업을 담당기사·시간을 비워 "
                    + "대기함으로 돌립니다(작업중·완료 작업은 그대로). 비활성 기사는 로그인·배정 대상에서 빠집니다. 이미 비활성이면 아무것도 바꾸지 않고 "
                    + "성공합니다. 같은 기사의 일정 변경이나 수락·거절·시작 요청을 기다리다 대기 한도를 넘기면 ORGANIZATION-024입니다. "
                    + TECHNICIAN_TARGET
                    + BUSY
                    + " "
                    + OWNER_ONLY,
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponse(responseCode = "204", description = "비활성화 성공")
    @ApiErrorCodes(
            enumClass = CommonErrorCode.class,
            includes = {"BAD_REQUEST", "UNAUTHORIZED"})
    @ApiErrorCodes(
            enumClass = OrganizationErrorCode.class,
            includes = {
                "NOT_ORGANIZATION_MEMBER",
                "OWNER_ONLY",
                "TECHNICIAN_NOT_FOUND",
                "ORGANIZATION_BUSY",
                "TECHNICIAN_SCHEDULE_BUSY"
            })
    void deactivateTechnician(
            @Parameter(hidden = true) AccountPrincipal requester,
            @Parameter(description = "발주사 식별자", example = "1", required = true) Long organizationId,
            @Parameter(description = "기사 계약 식별자(참여 인원 목록의 memberId)", example = "5", required = true)
                    Long technicianId);

    @Operation(
            summary = "기사 계약 재활성화",
            description =
                    REACTIVATE + "계약 식별자가 그대로라 이전 배정·작업 이력이 이어집니다. " + TECHNICIAN_TARGET + BUSY + " " + OWNER_ONLY,
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponse(responseCode = "204", description = "재활성화 성공")
    @ApiErrorCodes(
            enumClass = CommonErrorCode.class,
            includes = {"BAD_REQUEST", "UNAUTHORIZED"})
    @ApiErrorCodes(
            enumClass = OrganizationErrorCode.class,
            includes = {
                "NOT_ORGANIZATION_MEMBER",
                "OWNER_ONLY",
                "TECHNICIAN_NOT_FOUND",
                "ACTIVE_IN_OTHER_ROLE",
                "NOT_LAST_ROLE",
                "ORGANIZATION_BUSY"
            })
    void reactivateTechnician(
            @Parameter(hidden = true) AccountPrincipal requester,
            @Parameter(description = "발주사 식별자", example = "1", required = true) Long organizationId,
            @Parameter(description = "기사 계약 식별자(참여 인원 목록의 memberId)", example = "5", required = true)
                    Long technicianId);

    @Operation(
            summary = "직원을 기사로 역할 변경",
            description = "직원 소속을 기사로 바꿉니다. 총관리자 소속(요청자 자신 포함)은 바꿀 수 없어 ORGANIZATION-025입니다. 그 직원이 등록한 작업 중 "
                    + "끝나지 않은 작업(대기함·배정됨·작업전·작업중)이 있으면 ORGANIZATION-028입니다. 확인 창에 보일 그 작업은 작업 목록 API를 "
                    + "registrarId·statuses로 조회합니다. "
                    + ROLE_CHANGE
                    + STAFF_TARGET
                    + BUSY
                    + " "
                    + OWNER_ONLY,
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponse(
            responseCode = "200",
            description = "역할 변경 성공",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ParticipantRoleChangeResponse.class)))
    @ApiErrorCodes(
            enumClass = CommonErrorCode.class,
            includes = {"BAD_REQUEST", "UNAUTHORIZED"})
    @ApiErrorCodes(
            enumClass = OrganizationErrorCode.class,
            includes = {
                "NOT_ORGANIZATION_MEMBER",
                "OWNER_ONLY",
                "MEMBERSHIP_NOT_FOUND",
                "OWNER_ROLE_LOCKED",
                "INACTIVE_MEMBER_ROLE",
                "CATEGORY_NOT_FOUND",
                "STAFF_HAS_UNFINISHED_WORKS",
                "ORGANIZATION_BUSY"
            })
    ParticipantRoleChangeResponse changeStaffToTechnician(
            @Parameter(hidden = true) AccountPrincipal requester,
            @Parameter(description = "발주사 식별자", example = "1", required = true) Long organizationId,
            @Parameter(description = "직원 소속 식별자(참여 인원 목록의 memberId)", example = "3", required = true) Long membershipId,
            ParticipantRoleChangeRequest request);

    @Operation(
            summary = "기사를 직원으로 역할 변경",
            description = "기사 계약을 직원으로 바꿉니다. 작업중 작업이 있으면 아무것도 바꾸지 않고 ORGANIZATION-027입니다. 배정됨·작업전 작업은 같은 "
                    + "요청에서 담당기사·시간을 비워 대기함으로 돌리고 그 작업 식별자를 releasedWorkIds로 돌려줍니다. 확인 창에 보일 작업은 작업 목록 "
                    + "API를 technicianId·statuses로 조회합니다. 같은 기사의 일정 변경이나 수락·거절·시작 요청을 기다리다 대기 한도를 넘기면 "
                    + "ORGANIZATION-024입니다. "
                    + ROLE_CHANGE
                    + TECHNICIAN_TARGET
                    + BUSY
                    + " "
                    + OWNER_ONLY,
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponse(
            responseCode = "200",
            description = "역할 변경 성공",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ParticipantRoleChangeResponse.class)))
    @ApiErrorCodes(
            enumClass = CommonErrorCode.class,
            includes = {"BAD_REQUEST", "UNAUTHORIZED"})
    @ApiErrorCodes(
            enumClass = OrganizationErrorCode.class,
            includes = {
                "NOT_ORGANIZATION_MEMBER",
                "OWNER_ONLY",
                "TECHNICIAN_NOT_FOUND",
                "INACTIVE_MEMBER_ROLE",
                "CATEGORY_NOT_FOUND",
                "TECHNICIAN_WORK_IN_PROGRESS",
                "ORGANIZATION_BUSY",
                "TECHNICIAN_SCHEDULE_BUSY"
            })
    ParticipantRoleChangeResponse changeTechnicianToStaff(
            @Parameter(hidden = true) AccountPrincipal requester,
            @Parameter(description = "발주사 식별자", example = "1", required = true) Long organizationId,
            @Parameter(description = "기사 계약 식별자(참여 인원 목록의 memberId)", example = "5", required = true) Long technicianId,
            ParticipantRoleChangeRequest request);
}
