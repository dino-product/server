package com.orbit.profile.application.port.in.command.dto;

/** 필수 약관 두 가지는 모두 {@code true}여야 한다. {@code marketingAgreed}가 {@code null}이면 마케팅 동의를 바꾸지 않는다. */
public record AgreeToTermsCommand(
        Long requesterAccountId,
        Long accountId,
        boolean serviceTermsAgreed,
        boolean privacyPolicyAgreed,
        Boolean marketingAgreed) {}
