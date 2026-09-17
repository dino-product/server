package com.example.user.application.port.out;

import java.util.Optional;

import com.example.user.domain.User;
import com.example.user.domain.UserId;

public interface UserRepository {

    User save(User user);

    Optional<User> findById(UserId userId);
}
