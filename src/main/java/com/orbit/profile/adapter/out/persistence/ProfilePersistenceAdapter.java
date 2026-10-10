package com.orbit.profile.adapter.out.persistence;

import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.profile.application.port.out.ConcurrentProfileUpdateException;
import com.orbit.profile.application.port.out.ProfileRepository;
import com.orbit.profile.domain.AccountId;
import com.orbit.profile.domain.Profile;

/** 기존 프로필은 불러온 Entity에 값을 덮어써 변경 감지로 반영한다. 호출자 트랜잭션이 없어도 반영되도록 저장을 한 트랜잭션으로 묶는다. */
@Repository
class ProfilePersistenceAdapter implements ProfileRepository {

    private final SpringDataProfileRepository repository;

    ProfilePersistenceAdapter(SpringDataProfileRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Profile> findByAccountId(AccountId accountId) {
        return repository.findById(accountId.value()).map(ProfileJpaEntity::toDomain);
    }

    /**
     * 다른 요청이 먼저 저장했거나(버전 불일치) 첫 프로필을 동시에 만들면(기본 키 충돌) {@link ConcurrentProfileUpdateException}을 던진다. 충돌을 호출자에게
     * 바로 알리도록 즉시 flush한다.
     */
    @Override
    @Transactional
    public void save(Profile profile) {
        try {
            repository
                    .findById(profile.accountId().value())
                    .ifPresentOrElse(
                            entity -> entity.apply(profile), () -> repository.save(ProfileJpaEntity.from(profile)));
            repository.flush();
        } catch (OptimisticLockingFailureException | DataIntegrityViolationException exception) {
            throw new ConcurrentProfileUpdateException(profile.accountId(), exception);
        }
    }
}
