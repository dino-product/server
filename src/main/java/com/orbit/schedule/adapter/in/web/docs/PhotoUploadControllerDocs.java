package com.orbit.schedule.adapter.in.web.docs;

import com.orbit.auth.AccountPrincipal;
import com.orbit.schedule.adapter.in.web.photo.PhotoUploadRequest;
import com.orbit.schedule.adapter.in.web.photo.PhotoUploadResponse;
import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.shared.error.CommonErrorCode;
import com.orbit.shared.openapi.ApiErrorCodes;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Photo uploads", description = "현장 사진·완료보고 사진 업로드 주소 발급 API")
public interface PhotoUploadControllerDocs {

    @Operation(
            summary = "현장 사진 업로드 주소 발급",
            description = "작업 등록·기본정보 수정에 붙일 현장 사진 한 장의 업로드 주소를 발급합니다. 사진 바이트는 서버를 거치지 않고 "
                    + "uploadUrl에 requiredHeaders를 붙여 PUT으로 올립니다(10분 안에, content-length는 HTTP 클라이언트가 본문 길이로 붙임). "
                    + "올린 뒤 fileId를 sitePhotoIds에 넣습니다. "
                    + "그 조직의 총관리자·직원만 받을 수 있습니다. 이미지(JPEG·PNG·WebP·HEIC/HEIF)가 아니면 SCHEDULE-020, "
                    + "크기가 현장 사진 상한을 넘으면 SCHEDULE-021, 형식·크기가 없거나 크기가 1 미만이면 SCHEDULE-003입니다. "
                    + "요청자가 그 조직의 구성원이 아니면 SCHEDULE-004, 기사면 SCHEDULE-005입니다. "
                    + "서버가 주소를 서명하지 못하면(저장소 자격 증명 오류) SCHEDULE-023(503, 잠시 뒤 다시 시도)입니다.",
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponse(
            responseCode = "200",
            description = "발급함",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PhotoUploadResponse.class)))
    @ApiErrorCodes(
            enumClass = CommonErrorCode.class,
            includes = {"BAD_REQUEST", "UNAUTHORIZED"})
    @ApiErrorCodes(
            enumClass = ScheduleErrorCode.class,
            includes = {
                "INVALID_WORK_INPUT",
                "NOT_ORGANIZATION_MEMBER",
                "ACTION_NOT_ALLOWED",
                "PHOTO_TYPE_NOT_ALLOWED",
                "PHOTO_TOO_LARGE",
                "PHOTO_STORAGE_UNAVAILABLE"
            })
    PhotoUploadResponse issueSitePhotoUpload(
            @Parameter(hidden = true) AccountPrincipal requester,
            @Parameter(description = "조직(발주사) 식별자", example = "1", required = true) Long organizationId,
            PhotoUploadRequest request);

    @Operation(
            summary = "완료보고 사진 업로드 주소 발급",
            description = "완료보고에 붙일 사진(작업 전·후) 한 장의 업로드 주소를 발급합니다. 사진은 uploadUrl에 requiredHeaders를 붙여 "
                    + "PUT으로 올리고(10분 안에, content-length는 HTTP 클라이언트가 본문 길이로 붙임), 올린 뒤 fileId를 완료보고 제출"
                    + "(works/{workId}/completion-report)의 beforePhotoIds·afterPhotoIds에 넣습니다. "
                    + "담당 기사 본인이 자기 작업중 작업에만 받을 수 있습니다. 장당 40MB를 넘으면 SCHEDULE-021, "
                    + "이미지(JPEG·PNG·WebP·HEIC/HEIF)가 아니면 SCHEDULE-020, 형식·크기가 없거나 크기가 1 미만이면 SCHEDULE-003, "
                    + "작업중이 아니면 SCHEDULE-002입니다. 요청자가 그 조직의 구성원이 아니면 SCHEDULE-004, 관리자면 SCHEDULE-005, "
                    + "작업이 없거나 다른 조직의 작업이거나 기사가 볼 수 없는 작업이면 SCHEDULE-001입니다. "
                    + "서버가 주소를 서명하지 못하면(저장소 자격 증명 오류) SCHEDULE-023(503, 잠시 뒤 다시 시도)입니다.",
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponse(
            responseCode = "200",
            description = "발급함",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PhotoUploadResponse.class)))
    @ApiErrorCodes(
            enumClass = CommonErrorCode.class,
            includes = {"BAD_REQUEST", "UNAUTHORIZED"})
    @ApiErrorCodes(
            enumClass = ScheduleErrorCode.class,
            includes = {
                "INVALID_WORK_INPUT",
                "NOT_ORGANIZATION_MEMBER",
                "ACTION_NOT_ALLOWED",
                "WORK_NOT_FOUND",
                "INVALID_WORK_STATE",
                "PHOTO_TYPE_NOT_ALLOWED",
                "PHOTO_TOO_LARGE",
                "PHOTO_STORAGE_UNAVAILABLE"
            })
    PhotoUploadResponse issueCompletionPhotoUpload(
            @Parameter(hidden = true) AccountPrincipal requester,
            @Parameter(description = "조직(발주사) 식별자", example = "1", required = true) Long organizationId,
            @Parameter(description = "작업 식별자", example = "10", required = true) Long workId,
            PhotoUploadRequest request);
}
