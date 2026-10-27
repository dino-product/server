package com.orbit.auth.adapter.out.persistence;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.auth.application.port.out.AppleRefreshToken;
import com.orbit.auth.application.port.out.AppleRefreshTokenRepository;
import com.orbit.auth.domain.AccountId;

@Repository
class AppleRefreshTokenPersistenceAdapter implements AppleRefreshTokenRepository {

    private final SpringDataAppleRefreshTokenRepository repository;
    private final AppleRefreshTokenCipher cipher;

    AppleRefreshTokenPersistenceAdapter(
            SpringDataAppleRefreshTokenRepository repository, AppleRefreshTokenCipher cipher) {
        this.repository = repository;
        this.cipher = cipher;
    }

    @Override
    @Transactional
    public void save(AccountId accountId, String clientId, String refreshToken, Instant updatedAt) {
        repository.upsert(
                accountId.value(), clientId, cipher.encrypt(refreshToken, context(accountId, clientId)), updatedAt);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AppleRefreshToken> listByAccount(AccountId accountId) {
        return repository.findByAccountId(accountId.value()).stream()
                .map(entity -> new AppleRefreshToken(
                        entity.clientId(),
                        cipher.decrypt(entity.encryptedToken(), context(accountId, entity.clientId()))))
                .toList();
    }

    @Override
    @Transactional
    public void delete(AccountId accountId, String clientId) {
        repository.deleteByAccountAndClient(accountId.value(), clientId);
    }

    @Override
    @Transactional
    public void deleteByAccount(AccountId accountId) {
        repository.deleteByAccount(accountId.value());
    }

    private static String context(AccountId accountId, String clientId) {
        return accountId.value() + ":" + clientId;
    }
}
