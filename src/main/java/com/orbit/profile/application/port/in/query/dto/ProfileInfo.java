package com.orbit.profile.application.port.in.query.dto;

import java.time.Instant;

import com.orbit.profile.domain.SignupStatus;
import com.orbit.profile.domain.SignupStep;

/**
 * 프로필을 입력하기 전이면 이름·연락처가, 가입을 마치기 전이면 가입일·마케팅 동의 여부가 {@code null}이다. 연락처는 숫자 11자리다. 필수 약관이 개정돼 재동의가
 * 필요하면 활성 계정이라도 다음 단계가 약관 동의다.
 */
public record ProfileInfo(
        Long accountId,
        SignupStatus status,
        SignupStep nextStep,
        String name,
        String phoneNumber,
        Instant signedUpAt,
        Boolean marketingAgreed) {}
