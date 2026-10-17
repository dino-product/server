package com.orbit.profile.application.service;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.profile.ProfileLookup;
import com.orbit.profile.application.port.out.ProfileRepository;
import com.orbit.profile.domain.AccountId;

/** 이름 조회만 하므로 약관 동의 이력은 읽지 않는다. */
@Service
public class ProfileLookupService implements ProfileLookup {

    private final ProfileRepository profiles;

    public ProfileLookupService(ProfileRepository profiles) {
        this.profiles = profiles;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, String> findNames(Set<Long> accountIds) {
        if (accountIds.isEmpty()) {
            return Map.of();
        }
        Set<AccountId> ids = accountIds.stream().map(AccountId::new).collect(Collectors.toSet());
        return profiles.findActiveNames(ids).entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(entry -> entry.getKey().value(), entry -> entry.getValue()
                        .value()));
    }
}
