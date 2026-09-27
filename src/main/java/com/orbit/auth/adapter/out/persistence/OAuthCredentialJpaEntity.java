package com.orbit.auth.adapter.out.persistence;

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

import com.orbit.auth.domain.ExternalIdentity;
import com.orbit.auth.domain.OAuthProvider;

@Entity
@Table(
        name = "oauth_credentials",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_oauth_credentials_provider_uid",
                        columnNames = {"provider", "oauth_uid"}))
class OAuthCredentialJpaEntity {

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

    protected OAuthCredentialJpaEntity() {}

    private OAuthCredentialJpaEntity(AccountJpaEntity account, OAuthProvider provider, String subject) {
        this.account = account;
        this.provider = provider;
        this.subject = subject;
    }

    static OAuthCredentialJpaEntity from(AccountJpaEntity account, ExternalIdentity identity) {
        return new OAuthCredentialJpaEntity(account, identity.provider(), identity.subject());
    }

    ExternalIdentity toDomain() {
        return new ExternalIdentity(provider, subject);
    }
}
