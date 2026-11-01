package com.orbit.auth.adapter.out.persistence;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.auth.application.port.out.RefreshSessionRepository;
import com.orbit.auth.domain.AccountId;
import com.orbit.auth.domain.RefreshSession;
import com.orbit.auth.domain.RefreshSessionId;
import com.orbit.auth.domain.RefreshTokenHash;

@Repository
class RefreshSessionPersistenceAdapter implements RefreshSessionRepository {

    private final SpringDataRefreshSessionRepository repository;

    RefreshSessionPersistenceAdapter(SpringDataRefreshSessionRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void saveNew(RefreshSession session) {
        if (session.previousTokenHash().isPresent()) {
            throw new IllegalArgumentException("only new sessions can be saved");
        }
        repository.insert(
                session.id().value(),
                session.accountId().value(),
                session.tokenHash().value(),
                session.startedAt(),
                session.expiresAt());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<RefreshSession> findByIdForUpdate(RefreshSessionId id) {
        return repository.findLockedById(id.value()).map(RefreshSessionJpaEntity::toDomain);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void update(RefreshSession session) {
        repository
                .findById(session.id().value())
                .orElseThrow(() -> new IllegalArgumentException("refresh session not found"))
                .apply(session);
    }

    @Override
    @Transactional
    public void delete(RefreshSessionId id) {
        repository.deleteSession(id.value());
    }

    @Override
    @Transactional
    public void deleteOwnedBy(RefreshSessionId id, AccountId accountId) {
        repository.deleteSessionOfAccount(id.value(), accountId.value());
    }

    @Override
    @Transactional
    public void deleteByToken(RefreshSessionId id, RefreshTokenHash tokenHash) {
        repository.deleteSessionWithToken(id.value(), tokenHash.value());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteAllByAccount(AccountId accountId) {
        repository.deleteAllOfAccount(accountId.value());
    }

    @Override
    @Transactional
    public void deleteExpiredByAccount(AccountId accountId, Instant now) {
        repository.deleteExpiredOfAccount(accountId.value(), now);
    }
}
