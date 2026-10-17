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

    @Column(name = "closed_at")
    private Instant closedAt;

    @OneToMany(mappedBy = "account", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<OAuthCredentialJpaEntity> credentials = new ArrayList<>();

    protected AccountJpaEntity() {}

    private AccountJpaEntity(Instant registeredAt) {
        this.registeredAt = registeredAt;
    }

    static AccountJpaEntity from(Account account) {
        if (account.id().isPresent()) {
            throw new IllegalArgumentException("only new accounts can be saved");
        }
        AccountJpaEntity entity = new AccountJpaEntity(account.registeredAt());
        account.identities()
                .forEach(identity -> entity.credentials.add(OAuthCredentialJpaEntity.from(entity, identity)));
        return entity;
    }

    /** 도메인 계정의 폐쇄 시각을 반영하고 계정에 남지 않은 외부 식별 행을 지운다. */
    void apply(Account account) {
        if (!id.equals(account.id().map(AccountId::value).orElse(null))) {
            throw new IllegalArgumentException("account id does not match");
        }
        List<ExternalIdentity> identities = account.identities();
        if (!credentials.stream()
                .map(OAuthCredentialJpaEntity::toDomain)
                .toList()
                .containsAll(identities)) {
            throw new IllegalArgumentException("adding identities is not supported");
        }
        credentials.removeIf(credential -> !identities.contains(credential.toDomain()));
        this.closedAt = account.closedAt().orElse(null);
    }

    Account toDomain() {
        if (closedAt != null) {
            return Account.reconstituteClosed(new AccountId(id), registeredAt, closedAt);
        }
        List<ExternalIdentity> identities =
                credentials.stream().map(OAuthCredentialJpaEntity::toDomain).toList();
        return Account.reconstitute(new AccountId(id), identities, registeredAt);
    }
}
