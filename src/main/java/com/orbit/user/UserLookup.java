package com.orbit.user;

import java.util.Optional;

public interface UserLookup {

    Optional<UserSummary> findById(Long userId);
}
