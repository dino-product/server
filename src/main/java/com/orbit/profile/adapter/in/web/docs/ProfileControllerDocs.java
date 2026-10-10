package com.orbit.profile.adapter.in.web.docs;

import jakarta.validation.constraints.Positive;

import com.orbit.auth.AccountPrincipal;
import com.orbit.profile.adapter.in.web.AgreeToTermsRequest;
import com.orbit.profile.adapter.in.web.ProfileResponse;
import com.orbit.profile.adapter.in.web.SaveProfileRequest;
import com.orbit.profile.application.error.ProfileErrorCode;
import com.orbit.shared.error.CommonErrorCode;
import com.orbit.shared.openapi.ApiErrorCodes;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Profile", description = "가입 프로필·약관 동의와 가입 단계")
public interface ProfileControllerDocs {

    @Operation(
            summary = "내 프로필·가입 단계 조회",
            description = "본인 계정의 가입 상태와 다음 가입 단계, 입력해 둔 이름·연락처를 돌려줍니다. 카카오 인증 직후에는 가입 미완료이며 다음 단계는 "
                    + "프로필 입력입니다. 가입 중에 나갔다 다시 들어오면 이 값으로 입력 화면을 채웁니다. 다른 계정의 ID면 PROFILE-002입니다.",
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content =
                    @Content(mediaType = "application/json", schema = @Schema(implementation = ProfileResponse.class)))
    @ApiErrorCodes(
            enumClass = CommonErrorCode.class,
            includes = {"BAD_REQUEST", "UNAUTHORIZED", "NOT_FOUND"})
    @ApiErrorCodes(enumClass = ProfileErrorCode.class, includes = "PROFILE_NOT_FOUND")
    ProfileResponse getProfile(
            @Parameter(hidden = true) AccountPrincipal requester,
            @Parameter(description = "본인 계정 식별자", example = "1", required = true) @Positive Long accountId);

    @Operation(
            summary = "프로필 입력·수정",
            description = "이름·연락처를 저장하고 저장 뒤의 가입 단계를 돌려줍니다. 가입 미완료 계정은 다음 단계가 약관 동의가 되고, 가입을 마친 계정은 "
                    + "계정 정보 수정으로 쓰입니다. 규칙을 어기면 PROFILE-001, 다른 계정의 ID면 PROFILE-002, 같은 프로필을 다른 요청이 먼저 "
                    + "저장했으면 COMMON-409입니다.",
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponse(
            responseCode = "200",
            description = "저장 성공",
            content =
                    @Content(mediaType = "application/json", schema = @Schema(implementation = ProfileResponse.class)))
    @ApiErrorCodes(
            enumClass = CommonErrorCode.class,
            includes = {"BAD_REQUEST", "UNAUTHORIZED", "NOT_FOUND", "CONFLICT"})
    @ApiErrorCodes(
            enumClass = ProfileErrorCode.class,
            includes = {"INVALID_PROFILE_INPUT", "PROFILE_NOT_FOUND"})
    ProfileResponse saveProfile(
            @Parameter(hidden = true) AccountPrincipal requester,
            @Parameter(description = "본인 계정 식별자", example = "1", required = true) @Positive Long accountId,
            SaveProfileRequest request);

    @Operation(
            summary = "약관 동의·재동의",
            description = "필수 약관(서비스 이용약관·개인정보 처리방침) 동의와 선택 약관(마케팅 정보 수신) 값을 받아 약관별 현재 버전과 시각을 기록합니다. "
                    + "프로필을 입력한 가입 미완료 계정은 가입 완료가 되고, 필수 약관이 개정된 활성 계정은 재동의가 됩니다. 프로필 입력 전이면 PROFILE-003, "
                    + "필수 약관 중 하나라도 동의하지 않으면 PROFILE-004, 다른 계정의 ID면 PROFILE-002, 같은 프로필을 다른 요청이 먼저 저장했으면 "
                    + "COMMON-409입니다.",
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponse(
            responseCode = "200",
            description = "동의 성공",
            content =
                    @Content(mediaType = "application/json", schema = @Schema(implementation = ProfileResponse.class)))
    @ApiErrorCodes(
            enumClass = CommonErrorCode.class,
            includes = {"BAD_REQUEST", "UNAUTHORIZED", "NOT_FOUND", "CONFLICT"})
    @ApiErrorCodes(
            enumClass = ProfileErrorCode.class,
            includes = {"PROFILE_NOT_FOUND", "PROFILE_REQUIRED", "REQUIRED_TERMS_NOT_AGREED"})
    ProfileResponse agreeToTerms(
            @Parameter(hidden = true) AccountPrincipal requester,
            @Parameter(description = "본인 계정 식별자", example = "1", required = true) @Positive Long accountId,
            AgreeToTermsRequest request);
}
