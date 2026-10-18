package com.orbit.auth.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * 로그인 한 번으로 시작하는 기기 세션. refresh token은 쓸 때마다 다음 토큰으로 회전하고, 이미 회전된 토큰이 다시 오면 탈취로 본다. 같은 토큰으로 거의 동시에
 * 보낸 재발급은 직전 토큰을 유예 동안 받아 이미 회전한 다음 토큰을 다시 돌려주므로 서로를 재사용으로 만들지 않는다. 미사용 수명은 회전할 때마다 그 시각부터
 * 다시 센다. 토큰 원문은 두지 않고 해시만 보관한다.
 */
public final class RefreshSession {

    /** 회전한 뒤 직전 토큰을 다시 받아 줄 시간. */
    public static final Duration REPLAY_GRACE = Duration.ofSeconds(30);

    private final RefreshSessionId id;
    private final AccountId accountId;
    private final Instant startedAt;
    private RefreshTokenHash tokenHash;
    private RefreshTokenHash previousTokenHash;
    private Instant rotatedAt;
    private Instant expiresAt;

    private RefreshSession(
            RefreshSessionId id,
            AccountId accountId,
            RefreshTokenHash tokenHash,
            RefreshTokenHash previousTokenHash,
            Instant rotatedAt,
            Instant startedAt,
            Instant expiresAt) {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        if (accountId == null) {
            throw new IllegalArgumentException("accountId must not be null");
        }
        if (tokenHash == null) {
            throw new IllegalArgumentException("tokenHash must not be null");
        }
        if ((previousTokenHash == null) != (rotatedAt == null)) {
            throw new IllegalArgumentException("previousTokenHash and rotatedAt must be both present or both absent");
        }
        if (startedAt == null) {
            throw new IllegalArgumentException("startedAt must not be null");
        }
        if (expiresAt == null || !expiresAt.isAfter(startedAt)) {
            throw new IllegalArgumentException("expiresAt must be after startedAt");
        }
        this.id = id;
        this.accountId = accountId;
        this.tokenHash = tokenHash;
        this.previousTokenHash = previousTokenHash;
        this.rotatedAt = rotatedAt;
        this.startedAt = startedAt;
        this.expiresAt = expiresAt;
    }

    /** 로그인으로 첫 토큰을 발급하며 세션을 시작한다. */
    public static RefreshSession start(
            RefreshSessionId id,
            AccountId accountId,
            RefreshTokenHash tokenHash,
            Instant startedAt,
            Duration idleLifetime) {
        requirePositive(idleLifetime);
        if (startedAt == null) {
            throw new IllegalArgumentException("startedAt must not be null");
        }
        return new RefreshSession(id, accountId, tokenHash, null, null, startedAt, startedAt.plus(idleLifetime));
    }

    public static RefreshSession reconstitute(
            RefreshSessionId id,
            AccountId accountId,
            RefreshTokenHash tokenHash,
            RefreshTokenHash previousTokenHash,
            Instant rotatedAt,
            Instant startedAt,
            Instant expiresAt) {
        return new RefreshSession(id, accountId, tokenHash, previousTokenHash, rotatedAt, startedAt, expiresAt);
    }

    /**
     * 제출된 토큰을 판정한다. 현재 토큰이면 {@code next}로 회전하고 만료를 연장한다. {@code next}는 호출자가 제출된 비밀값에서 결정적으로 파생한 다음 토큰의
     * 해시이므로, 유예 안에 직전 토큰이 다시 오면 같은 {@code next}가 현재 토큰과 같은지로 다시 보내도 되는지 안다. 회전 말고는 상태를 바꾸지 않는다.
     */
    public RefreshOutcome refresh(
            RefreshTokenHash presented, RefreshTokenHash next, Instant now, Duration idleLifetime) {
        if (presented == null) {
            throw new IllegalArgumentException("presented must not be null");
        }
        if (next == null) {
            throw new IllegalArgumentException("next must not be null");
        }
        if (now == null) {
            throw new IllegalArgumentException("now must not be null");
        }
        requirePositive(idleLifetime);
        if (!now.isBefore(expiresAt)) {
            return RefreshOutcome.EXPIRED;
        }
        if (presented.equals(tokenHash)) {
            previousTokenHash = tokenHash;
            tokenHash = next;
            rotatedAt = now;
            expiresAt = now.plus(idleLifetime);
            return RefreshOutcome.ROTATED;
        }
        if (presented.equals(previousTokenHash) && now.isBefore(rotatedAt.plus(REPLAY_GRACE))) {
            return next.equals(tokenHash) ? RefreshOutcome.REPLAYED : RefreshOutcome.STALE;
        }
        return RefreshOutcome.REUSED;
    }

    private static void requirePositive(Duration idleLifetime) {
        if (idleLifetime == null || idleLifetime.isZero() || idleLifetime.isNegative()) {
            throw new IllegalArgumentException("idleLifetime must be positive");
        }
    }

    public RefreshSessionId id() {
        return id;
    }

    public AccountId accountId() {
        return accountId;
    }

    public RefreshTokenHash tokenHash() {
        return tokenHash;
    }

    public Optional<RefreshTokenHash> previousTokenHash() {
        return Optional.ofNullable(previousTokenHash);
    }

    public Optional<Instant> rotatedAt() {
        return Optional.ofNullable(rotatedAt);
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    @Override
    public String toString() {
        return "RefreshSession[id=" + id.value() + ", accountId=" + accountId.value() + ", expiresAt=" + expiresAt
                + "]";
    }
}
