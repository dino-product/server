package com.orbit.profile.domain;

import java.time.Instant;
import java.util.Objects;

/** 한 번의 약관 동의 기록. 개정 이력을 남기기 위해 종류·버전·시각을 함께 보관하고 지우지 않는다. */
public record TermsAgreement(TermsType type, String version, Instant agreedAt) {

    public TermsAgreement {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(agreedAt, "agreedAt must not be null");
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("version must not be blank");
        }
    }
}
