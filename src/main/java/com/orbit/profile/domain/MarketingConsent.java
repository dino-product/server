package com.orbit.profile.domain;

import java.time.Instant;
import java.util.Objects;

/** 마케팅 정보 수신 동의의 현재 값과 마지막으로 바뀐 시각. */
public record MarketingConsent(boolean agreed, Instant changedAt) {

    public MarketingConsent {
        Objects.requireNonNull(changedAt, "changedAt must not be null");
    }
}
