package com.orbit.profile.application.port.out;

import java.util.Optional;

import com.orbit.profile.domain.AccountId;
import com.orbit.profile.domain.Profile;

public interface ProfileRepository {

    Optional<Profile> findByAccountId(AccountId accountId);

    /** 새 프로필은 만들고 이미 있는 프로필은 바뀐 값으로 덮어쓴다. */
    void save(Profile profile);
}
