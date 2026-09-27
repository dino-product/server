package com.orbit.auth.adapter.out.persistence;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import com.orbit.auth.domain.Account;
import com.orbit.auth.domain.AccountId;
import com.orbit.auth.domain.ExternalIdentity;

@Entity
@Table(name = "accounts")
class AccountJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    @OneToMany(mappedBy = "account", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<OAuthCredentialJpaEntity> credentials = new ArrayList<>();

    protected AccountJpaEntity() {}

    private AccountJpaEntity(Instant registeredAt) {
        this.registeredAt = registeredAt;
    }

    static AccountJpaEntity from(Account account) {
        AccountJpaEntity entity = new AccountJpaEntity(account.registeredAt());
        account.identities()
                .forEach(identity -> entity.credentials.add(OAuthCredentialJpaEntity.from(entity, identity)));
        return entity;
    }

    Account toDomain() {
        List<ExternalIdentity> identities =
                credentials.stream().map(OAuthCredentialJpaEntity::toDomain).toList();
        return Account.reconstitute(new AccountId(id), identities, registeredAt);
    }
}
