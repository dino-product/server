package com.orbit.organization.adapter.out.profile;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.orbit.organization.application.port.out.AccountNamesPort;
import com.orbit.organization.domain.AccountId;
import com.orbit.profile.ProfileLookup;

/** profile의 이름 조회 공개 계약을 organization 계정 식별자로 옮기는 ACL 어댑터. 이름이 없는 계정의 표시는 소비자가 정한다. */
@Component
class ProfileAccountNamesAdapter implements AccountNamesPort {

    private final ProfileLookup profileLookup;

    ProfileAccountNamesAdapter(ProfileLookup profileLookup) {
        this.profileLookup = profileLookup;
    }

    @Override
    public Map<AccountId, String> findNames(Set<AccountId> accountIds) {
        if (accountIds.isEmpty()) {
            return Map.of();
        }
        Set<Long> ids = accountIds.stream().map(AccountId::value).collect(Collectors.toSet());
        return profileLookup.findNames(ids).entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(entry -> new AccountId(entry.getKey()), Map.Entry::getValue));
    }
}
