package com.example.user.adapter.out.persistence;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.example.user.application.port.out.UserRepository;
import com.example.user.domain.User;
import com.example.user.domain.UserId;

@Repository
class UserPersistenceAdapter implements UserRepository {

    private final SpringDataUserRepository repository;

    UserPersistenceAdapter(SpringDataUserRepository repository) {
        this.repository = repository;
    }

    @Override
    public User save(User user) {
        return repository.save(UserJpaEntity.from(user)).toDomain();
    }

    @Override
    public Optional<User> findById(UserId userId) {
        return repository.findById(userId.value()).map(UserJpaEntity::toDomain);
    }
}
