package com.orbit.notification.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * 계정이 앱 푸시를 받는 기기. 푸시 제공자가 앱에 발급한 기기 토큰 하나가 기기 하나이며, 토큰은 마지막으로 등록한 계정 하나에만 속한다. 토큰을 아는 사람은 그 기기로
 * 푸시를 보낼 수 있으므로 문자열 표현에는 앞 몇 글자와 길이만 남긴다.
 */
public final class PushDevice {

    public static final int MAX_TOKEN_LENGTH = 1024;

    private static final int VISIBLE_TOKEN_PREFIX = 6;

    private final Long id;
    private final Long accountId;
    private final PushPlatform platform;
    private final String token;
    private final Instant registeredAt;

    private PushDevice(Long id, Long accountId, PushPlatform platform, String token, Instant registeredAt) {
        if (id != null && id <= 0) {
            throw new IllegalArgumentException("id must be positive");
        }
        if (accountId == null || accountId <= 0) {
            throw new IllegalArgumentException("accountId must be positive");
        }
        if (platform == null) {
            throw new IllegalArgumentException("platform must not be null");
        }
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token must not be blank");
        }
        if (token.length() > MAX_TOKEN_LENGTH) {
            throw new IllegalArgumentException("token must be at most " + MAX_TOKEN_LENGTH + " characters");
        }
        if (registeredAt == null) {
            throw new IllegalArgumentException("registeredAt must not be null");
        }
        this.id = id;
        this.accountId = accountId;
        this.platform = platform;
        this.token = token;
        this.registeredAt = registeredAt;
    }

    /** 아직 저장하지 않은 등록. 같은 토큰이 이미 저장돼 있으면 저장소가 그 기기를 이 계정의 것으로 바꾼다. */
    public static PushDevice register(Long accountId, PushPlatform platform, String token, Instant registeredAt) {
        return new PushDevice(null, accountId, platform, token, registeredAt);
    }

    public static PushDevice reconstitute(
            Long id, Long accountId, PushPlatform platform, String token, Instant registeredAt) {
        return new PushDevice(
                Objects.requireNonNull(id, "id must not be null"), accountId, platform, token, registeredAt);
    }

    public boolean isOwnedBy(Long accountId) {
        return this.accountId.equals(accountId);
    }

    /** 로그에 남길 수 있는 토큰 표기. 앞 몇 글자와 전체 길이만 보인다. */
    public String maskedToken() {
        return token.substring(0, Math.min(VISIBLE_TOKEN_PREFIX, token.length())) + "...(" + token.length() + ")";
    }

    public Optional<Long> id() {
        return Optional.ofNullable(id);
    }

    public Long accountId() {
        return accountId;
    }

    public PushPlatform platform() {
        return platform;
    }

    public String token() {
        return token;
    }

    /** 마지막으로 등록한 시각. 앱은 시작할 때마다 다시 등록한다. */
    public Instant registeredAt() {
        return registeredAt;
    }

    @Override
    public String toString() {
        return "PushDevice[id=" + id + ", accountId=" + accountId + ", platform=" + platform + ", token="
                + maskedToken() + ", registeredAt=" + registeredAt + "]";
    }
}
