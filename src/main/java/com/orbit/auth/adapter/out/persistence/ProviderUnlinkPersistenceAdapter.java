package com.orbit.auth.adapter.out.persistence;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.auth.application.port.out.ProviderUnlinkRepository;
import com.orbit.auth.domain.AccountId;
import com.orbit.auth.domain.OAuthProvider;
import com.orbit.auth.domain.ProviderUnlink;

@Repository
class ProviderUnlinkPersistenceAdapter implements ProviderUnlinkRepository {

    private final SpringDataProviderUnlinkRepository repository;
    private final SpringDataAccountRepository accounts;

    ProviderUnlinkPersistenceAdapter(
            SpringDataProviderUnlinkRepository repository, SpringDataAccountRepository accounts) {
        this.repository = repository;
        this.accounts = accounts;
    }

    @Override
    @Transactional
    public void saveAll(List<ProviderUnlink> unlinks) {
        repository.saveAll(unlinks.stream()
                .map(unlink -> ProviderUnlinkJpaEntity.from(
                        accounts.getReferenceById(unlink.accountId().value()), unlink))
                .toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProviderUnlink> listByAccount(AccountId accountId) {
        return repository.findByAccountId(accountId.value()).stream()
                .map(ProviderUnlinkJpaEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProviderUnlink> listDue(Instant now, int limit) {
        return repository.findDue(now, Limit.of(limit)).stream()
                .map(ProviderUnlinkJpaEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public void update(ProviderUnlink unlink) {
        repository.updateRetry(
                unlink.accountId().value(), unlink.provider(), unlink.failedAttempts(), unlink.nextAttemptAt());
    }

    @Override
    @Transactional
    public void delete(AccountId accountId, OAuthProvider provider) {
        repository.deleteByAccountAndProvider(accountId.value(), provider);
    }
}
