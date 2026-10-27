package com.orbit.auth.adapter.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import com.orbit.auth.domain.AccountId;
import com.orbit.auth.domain.OAuthProvider;
import com.orbit.auth.domain.ProviderUnlink;

/** 닫힌 계정의 제공자 연결 해제 대기. 계정·제공자마다 하나만 두고, 주기 재시도는 다음 시도 시각 순으로 읽는다. */
@Entity
@Table(
        name = "provider_unlinks",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_provider_unlinks_account_provider",
                        columnNames = {"account_id", "provider"}),
        indexes = @Index(name = "idx_provider_unlinks_next_attempt_at", columnList = "next_attempt_at"))
class ProviderUnlinkJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private AccountJpaEntity account;

    @Column(name = "account_id", insertable = false, updatable = false)
    private Long accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 16)
    private OAuthProvider provider;

    @Column(name = "oauth_uid", length = 64)
    private String subject;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    protected ProviderUnlinkJpaEntity() {}

    private ProviderUnlinkJpaEntity(AccountJpaEntity account, ProviderUnlink unlink) {
        this.account = account;
        this.provider = unlink.provider();
        this.subject = unlink.subject();
        this.requestedAt = unlink.requestedAt();
        this.failedAttempts = unlink.failedAttempts();
        this.nextAttemptAt = unlink.nextAttemptAt();
    }

    static ProviderUnlinkJpaEntity from(AccountJpaEntity account, ProviderUnlink unlink) {
        return new ProviderUnlinkJpaEntity(account, unlink);
    }

    ProviderUnlink toDomain() {
        return new ProviderUnlink(
                new AccountId(accountId), provider, subject, requestedAt, failedAttempts, nextAttemptAt);
    }
}
