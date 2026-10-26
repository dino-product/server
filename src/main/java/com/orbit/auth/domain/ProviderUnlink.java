package com.orbit.auth.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 닫힌 계정과 외부 제공자의 연결을 끊는 할 일. 계정·제공자마다 하나이며, 실패하면 간격을 늘려 다시 시도하다가 요청 7일 뒤를 넘기면 포기한다.
 *
 * <p>카카오 연결 끊기는 회원번호가 필요하므로 계정에서 외부 식별을 지운 뒤에도 해제가 끝날 때까지 회원번호를 남긴다. Apple은 계정에 보관한 refresh
 * token으로 철회하므로 회원번호를 남기지 않는다. 문자열 표현에서 회원번호를 가린다.
 */
public record ProviderUnlink(
        AccountId accountId,
        OAuthProvider provider,
        String subject,
        Instant requestedAt,
        int failedAttempts,
        Instant nextAttemptAt) {

    /** 폐쇄 커밋 직후의 첫 시도가 실패할 때를 대비해 잡아 두는 첫 재시도 시각. 첫 시도와 주기 재시도가 겹치지 않게 한다. */
    private static final Duration FIRST_RETRY_DELAY = Duration.ofMinutes(1);

    private static final List<Duration> BACKOFF = List.of(
            Duration.ofMinutes(1),
            Duration.ofMinutes(5),
            Duration.ofMinutes(30),
            Duration.ofHours(2),
            Duration.ofHours(12));
    private static final Duration DAILY = Duration.ofDays(1);
    private static final Duration GIVE_UP_AFTER = Duration.ofDays(7);

    public ProviderUnlink {
        if (accountId == null) {
            throw new IllegalArgumentException("accountId must not be null");
        }
        if (provider == null) {
            throw new IllegalArgumentException("provider must not be null");
        }
        if (provider == OAuthProvider.KAKAO && (subject == null || subject.isBlank())) {
            throw new IllegalArgumentException("kakao unlink requires subject");
        }
        if (provider == OAuthProvider.APPLE && subject != null) {
            throw new IllegalArgumentException("apple unlink must not keep subject");
        }
        if (requestedAt == null || nextAttemptAt == null) {
            throw new IllegalArgumentException("requestedAt and nextAttemptAt must not be null");
        }
        if (failedAttempts < 0) {
            throw new IllegalArgumentException("failedAttempts must not be negative");
        }
    }

    /** 계정에서 끊은 외부 식별의 제공자 연결 해제를 요청한다. */
    public static ProviderUnlink request(AccountId accountId, ExternalIdentity identity, Instant requestedAt) {
        if (identity == null) {
            throw new IllegalArgumentException("identity must not be null");
        }
        if (requestedAt == null) {
            throw new IllegalArgumentException("requestedAt must not be null");
        }
        String subject = identity.provider() == OAuthProvider.KAKAO ? identity.subject() : null;
        return new ProviderUnlink(
                accountId, identity.provider(), subject, requestedAt, 0, requestedAt.plus(FIRST_RETRY_DELAY));
    }

    /** 시도가 실패한 뒤의 다음 재시도. 다음 시도가 요청 7일 뒤를 넘으면 포기하므로 빈 값이다. */
    public Optional<ProviderUnlink> afterFailure(Instant failedAt) {
        int attempts = failedAttempts + 1;
        Instant next = failedAt.plus(attempts <= BACKOFF.size() ? BACKOFF.get(attempts - 1) : DAILY);
        if (next.isAfter(requestedAt.plus(GIVE_UP_AFTER))) {
            return Optional.empty();
        }
        return Optional.of(new ProviderUnlink(accountId, provider, subject, requestedAt, attempts, next));
    }

    @Override
    public String toString() {
        return "ProviderUnlink[accountId=" + accountId.value() + ", provider=" + provider + ", requestedAt="
                + requestedAt + ", failedAttempts=" + failedAttempts + ", nextAttemptAt=" + nextAttemptAt + "]";
    }
}
