package com.orbit.auth.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 외부 인증으로 식별되는 계정 애그리게잇 루트. 첫 로그인에 등록되고 이후 로그인은 연결된 외부 식별로 같은 계정을 찾는다. 이름·연락처 같은 프로필과 조직 역할은
 * 소유하지 않는다. 닫힌 계정은 외부 식별이 없어 다시 찾을 수 없고, 식별자는 다른 모듈의 기록이 참조하므로 남는다.
 */
public final class Account {

    private final AccountId id;
    private final List<ExternalIdentity> identities;
    private final Instant registeredAt;
    private Instant closedAt;

    private Account(AccountId id, List<ExternalIdentity> identities, Instant registeredAt, Instant closedAt) {
        if (closedAt == null && (identities == null || identities.isEmpty())) {
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
        this.closedAt = closedAt;
    }

    /** 첫 로그인에 확인된 외부 식별로 계정을 등록한다. */
    public static Account register(ExternalIdentity identity, Instant registeredAt) {
        if (identity == null) {
            throw new IllegalArgumentException("identity must not be null");
        }
        return new Account(null, List.of(identity), registeredAt, null);
    }

    public static Account reconstitute(AccountId id, List<ExternalIdentity> identities, Instant registeredAt) {
        return new Account(Objects.requireNonNull(id, "id must not be null"), identities, registeredAt, null);
    }

    public static Account reconstituteClosed(AccountId id, Instant registeredAt, Instant closedAt) {
        if (closedAt == null) {
            throw new IllegalArgumentException("closedAt must not be null");
        }
        return new Account(Objects.requireNonNull(id, "id must not be null"), List.of(), registeredAt, closedAt);
    }

    /**
     * 계정을 닫는다. 연결된 외부 식별을 모두 끊어 같은 외부 계정의 다음 로그인이 새 계정을 만들게 하고, 끊은 식별마다 제공자 쪽 연결 해제할 일을 돌려준다. 이미
     * 닫힌 계정이면 아무것도 바꾸지 않고 빈 목록을 돌려준다.
     */
    public List<ProviderUnlink> close(Instant closedAt) {
        if (closedAt == null) {
            throw new IllegalArgumentException("closedAt must not be null");
        }
        if (isClosed()) {
            return List.of();
        }
        if (id == null) {
            throw new IllegalStateException("only saved accounts can be closed");
        }
        if (closedAt.isBefore(registeredAt)) {
            throw new IllegalArgumentException("closedAt must not be before registeredAt");
        }
        List<ProviderUnlink> unlinks = identities.stream()
                .map(identity -> new ProviderUnlink(id, identity, closedAt))
                .toList();
        identities.clear();
        this.closedAt = closedAt;
        return unlinks;
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

    public boolean isClosed() {
        return closedAt != null;
    }

    public Optional<Instant> closedAt() {
        return Optional.ofNullable(closedAt);
    }
}
