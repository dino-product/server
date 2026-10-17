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
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import com.orbit.auth.domain.OAuthProvider;
import com.orbit.auth.domain.ProviderUnlink;

/** 닫힌 계정의 제공자 연결 해제 대기. 계정·제공자마다 하나만 둔다. */
@Entity
@Table(
        name = "provider_unlinks",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_provider_unlinks_account_provider",
                        columnNames = {"account_id", "provider"}))
class ProviderUnlinkJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private AccountJpaEntity account;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 16)
    private OAuthProvider provider;

    @Column(name = "oauth_uid", nullable = false, length = 64)
    private String subject;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    protected ProviderUnlinkJpaEntity() {}

    private ProviderUnlinkJpaEntity(
            AccountJpaEntity account, OAuthProvider provider, String subject, Instant requestedAt) {
        this.account = account;
        this.provider = provider;
        this.subject = subject;
        this.requestedAt = requestedAt;
    }

    static ProviderUnlinkJpaEntity from(AccountJpaEntity account, ProviderUnlink unlink) {
        return new ProviderUnlinkJpaEntity(
                account, unlink.identity().provider(), unlink.identity().subject(), unlink.requestedAt());
    }
}
