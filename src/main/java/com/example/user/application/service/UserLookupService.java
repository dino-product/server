package com.example.user.application.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.user.UserLookup;
import com.example.user.UserSummary;
import com.example.user.application.port.out.UserRepository;
import com.example.user.domain.UserId;

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
