package com.orbit.user.application.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.user.UserLookup;
import com.orbit.user.UserSummary;
import com.orbit.user.application.port.out.UserRepository;
import com.orbit.user.domain.UserId;

@Service
public class UserLookupService implements UserLookup {

    private final UserRepository userRepository;

    public UserLookupService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserSummary> findById(Long userId) {
        return userRepository
                .findById(new UserId(userId))
                .map(user -> new UserSummary(user.id().orElseThrow().value(), user.displayName()));
    }
}
