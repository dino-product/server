package com.orbit.auth.adapter.out.persistence;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.orbit.auth.application.port.out.AccountRepository;
import com.orbit.auth.domain.Account;
import com.orbit.auth.domain.ExternalIdentity;

@Repository
class AccountPersistenceAdapter implements AccountRepository {

    private final SpringDataAccountRepository repository;

    AccountPersistenceAdapter(SpringDataAccountRepository repository) {
        this.repository = repository;
    }

    @Override
    public Account save(Account account) {
        return repository.save(AccountJpaEntity.from(account)).toDomain();
    }

    @Override
    public Optional<Account> findByIdentity(ExternalIdentity identity) {
        return repository
                .findByCredential(identity.provider(), identity.subject())
                .map(AccountJpaEntity::toDomain);
    }
}
