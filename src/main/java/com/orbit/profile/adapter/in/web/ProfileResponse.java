package com.orbit.profile.adapter.in.web;

import com.orbit.profile.application.port.in.query.dto.ProfileInfo;

import io.swagger.v3.oas.annotations.media.Schema;

public record ProfileResponse(
        @Schema(description = "계정 식별자", example = "1") Long accountId,

        @Schema(
                description = "가입 상태. PENDING_SIGNUP(가입 미완료), ACTIVE(가입 완료)",
                example = "PENDING_SIGNUP",
                allowableValues = {"PENDING_SIGNUP", "ACTIVE"})
        String status,

        @Schema(
                description = "다음 가입 단계. PROFILE(프로필 입력), TERMS(약관 동의·재동의), COMPLETED(없음)",
                example = "TERMS",
                allowableValues = {"PROFILE", "TERMS", "COMPLETED"})
        String nextStep,

        @Schema(description = "이름. 프로필을 입력하기 전이면 없다", example = "홍길동")
        String name,

        @Schema(description = "휴대전화 번호 숫자 11자리. 화면에서 010-0000-0000으로 표시한다. 프로필을 입력하기 전이면 없다", example = "01012345678")
        String phoneNumber) {

    static ProfileResponse from(ProfileInfo info) {
        return new ProfileResponse(
                info.accountId(), info.status().name(), info.nextStep().name(), info.name(), info.phoneNumber());
    }
}
