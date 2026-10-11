package com.orbit.auth.adapter.out.persistence;

import java.util.List;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.auth.application.port.out.ProviderUnlinkRepository;
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
}
