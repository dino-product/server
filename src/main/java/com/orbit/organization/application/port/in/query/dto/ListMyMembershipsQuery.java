package com.orbit.organization.application.port.in.query.dto;

/** {@code accountId}는 인증 주체에서 얻은 요청자 계정이다. 다른 계정의 소속을 고르는 입력은 없다. */
public record ListMyMembershipsQuery(Long accountId) {}
