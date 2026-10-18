package com.orbit.auth.adapter.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.orbit.auth.domain.AccountId;
import com.orbit.auth.domain.RefreshSession;
import com.orbit.auth.domain.RefreshSessionId;
import com.orbit.auth.domain.RefreshTokenHash;

/** 기기 세션의 refresh token 해시와 회전 상태. 식별자는 발급 쪽이 정하므로 저장은 insert 쿼리로 하고 이 엔티티는 조회·회전 저장에 쓴다. */
@Entity
@Table(name = "refresh_sessions", indexes = @Index(name = "ix_refresh_sessions_account_id", columnList = "account_id"))
class RefreshSessionJpaEntity {

    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    /** 계정 외래 키를 두려는 읽기 전용 연관. 값은 {@link #accountId}로 쓴다. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, insertable = false, updatable = false)
    private AccountJpaEntity account;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "previous_token_hash", length = 64)
    private String previousTokenHash;

    @Column(name = "rotated_at")
    private Instant rotatedAt;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected RefreshSessionJpaEntity() {}

    RefreshSession toDomain() {
        return RefreshSession.reconstitute(
                new RefreshSessionId(id),
                new AccountId(accountId),
                new RefreshTokenHash(tokenHash),
                previousTokenHash == null ? null : new RefreshTokenHash(previousTokenHash),
                rotatedAt,
                startedAt,
                expiresAt);
    }

    /** 회전으로 바뀌는 값만 옮긴다. */
    void apply(RefreshSession session) {
        if (!id.equals(session.id().value())) {
            throw new IllegalArgumentException("refresh session id mismatch");
        }
        this.tokenHash = session.tokenHash().value();
        this.previousTokenHash =
                session.previousTokenHash().map(RefreshTokenHash::value).orElse(null);
        this.rotatedAt = session.rotatedAt().orElse(null);
        this.expiresAt = session.expiresAt();
    }
}
