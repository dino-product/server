package com.orbit.profile.application.port.in.command.dto;

/** {@code requesterAccountId}는 인증된 요청자, {@code accountId}는 경로가 가리키는 프로필의 계정이다. 본인 것만 저장할 수 있다. */
public record SaveProfileCommand(Long requesterAccountId, Long accountId, String name, String phoneNumber) {}
