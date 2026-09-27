package com.orbit.auth.adapter.out.persistence;

import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.auth.application.port.out.AccountRepository;
import com.orbit.auth.application.port.out.DuplicateIdentityException;
import com.orbit.auth.domain.Account;
import com.orbit.auth.domain.ExternalIdentity;

@Repository
class AccountPersistenceAdapter implements AccountRepository {

    private final SpringDataAccountRepository repository;

    AccountPersistenceAdapter(SpringDataAccountRepository repository) {
        this.repository = repository;
    }

    /** 유니크 제약 위반이 호출자 트랜잭션을 중단시키지 않도록 별도 트랜잭션에서 즉시 flush한다. */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Account saveNew(Account account) {
        try {
            return repository.saveAndFlush(AccountJpaEntity.from(account)).toDomain();
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateIdentityException(account.identities().getFirst(), exception);
        }
    }

    @Override
    public Optional<Account> findByIdentity(ExternalIdentity identity) {
        return repository
                .findByCredential(identity.provider(), identity.subject())
                .map(AccountJpaEntity::toDomain);
    }
}
