package com.orbit.organization.application.port.in.query.dto;

/** {@code accountId}는 탈퇴하려는 계정이다. */
public record GetWithdrawalBlockingOwnershipsQuery(Long accountId) {}
