package com.orbit.profile.application.port.in.query.dto;

/** {@code requesterAccountId}는 인증된 요청자, {@code accountId}는 경로가 가리키는 프로필의 계정이다. 본인 것만 조회할 수 있다. */
public record GetProfileQuery(Long requesterAccountId, Long accountId) {}
