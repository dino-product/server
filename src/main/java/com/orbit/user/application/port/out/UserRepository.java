package com.orbit.user.application.port.out;

import java.util.Optional;

import com.orbit.user.domain.User;
import com.orbit.user.domain.UserId;

public interface UserRepository {

    User save(User user);

    Optional<User> findById(UserId userId);
}
