package com.orbit.profile.application.port.out;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.orbit.profile.domain.AccountId;
import com.orbit.profile.domain.PersonName;
import com.orbit.profile.domain.Profile;

public interface ProfileRepository {

    Optional<Profile> findByAccountId(AccountId accountId);

    /** 가입을 마친 계정의 이름. 가입 미완료·없는 계정은 결과에 없다. */
    Map<AccountId, PersonName> findActiveNames(Set<AccountId> accountIds);

    /** 새 프로필은 만들고 이미 있는 프로필은 바뀐 값으로 덮어쓴다. 다른 요청이 먼저 저장했으면 {@link ConcurrentProfileUpdateException}. */
    void save(Profile profile);
}
