package com.orbit.profile.application.port.in.command.dto;

/** {@code agreed}가 {@code null}이면 입력 오류다. 본인 계정만 바꿀 수 있다. */
public record ChangeMarketingConsentCommand(Long requesterAccountId, Long accountId, Boolean agreed) {}
