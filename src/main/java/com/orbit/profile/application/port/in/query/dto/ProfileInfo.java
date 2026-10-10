package com.orbit.profile.application.port.in.query.dto;

import com.orbit.profile.domain.SignupStatus;
import com.orbit.profile.domain.SignupStep;

/** 프로필을 입력하기 전이면 이름·연락처는 {@code null}이다. 연락처는 숫자 11자리다. */
public record ProfileInfo(Long accountId, SignupStatus status, SignupStep nextStep, String name, String phoneNumber) {}
