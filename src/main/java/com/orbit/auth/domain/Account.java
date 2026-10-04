package com.orbit.auth.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 외부 인증으로 식별되는 계정 애그리게잇 루트. 첫 로그인에 등록되고 이후 로그인은 연결된 외부 식별로 같은 계정을 찾는다. 이름·연락처 같은 프로필과 조직 역할은
 * 소유하지 않는다.
 */
public final class Account {

    private final AccountId id;
    private final List<ExternalIdentity> identities;
    private final Instant registeredAt;

    private Account(AccountId id, List<ExternalIdentity> identities, Instant registeredAt) {
        if (identities == null || identities.isEmpty()) {
            throw new IllegalArgumentException("identities must not be empty");
        }
        if (identities.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("identities must not contain null");
        }
        if (identities.stream().map(ExternalIdentity::provider).distinct().count() != identities.size()) {
            throw new IllegalArgumentException("identities must have at most one per provider");
        }
        if (registeredAt == null) {
            throw new IllegalArgumentException("registeredAt must not be null");
        }
        this.id = id;
        this.identities = new ArrayList<>(identities);
        this.registeredAt = registeredAt;
    }

    /** 첫 로그인에 확인된 외부 식별로 계정을 등록한다. */
    public static Account register(ExternalIdentity identity, Instant registeredAt) {
        if (identity == null) {
            throw new IllegalArgumentException("identity must not be null");
        }
        return new Account(null, List.of(identity), registeredAt);
    }

    public static Account reconstitute(AccountId id, List<ExternalIdentity> identities, Instant registeredAt) {
        return new Account(Objects.requireNonNull(id, "id must not be null"), identities, registeredAt);
    }

    public Optional<AccountId> id() {
        return Optional.ofNullable(id);
    }

    public List<ExternalIdentity> identities() {
        return List.copyOf(identities);
    }

    public Instant registeredAt() {
        return registeredAt;
    }
}
