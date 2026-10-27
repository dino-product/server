package com.orbit.profile.application.port.in.command.dto;

/** 탈퇴 요청. 사유는 선택 입력이라 비었거나 null일 수 있다. */
public record WithdrawCommand(Long requesterAccountId, Long accountId, String reason) {}
