package com.orbit.profile.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.profile.application.port.in.query.CheckServiceAccessUseCase;
import com.orbit.profile.application.port.in.query.dto.CheckServiceAccessQuery;
import com.orbit.profile.application.port.out.CurrentTermsPort;
import com.orbit.profile.application.port.out.ProfileRepository;
import com.orbit.profile.domain.AccountId;

/** 프로필이 없거나 필수 약관 동의를 마치지 않은 계정, 개정된 필수 약관에 재동의하지 않은 계정은 쓸 수 없다. 공통 검사 인터셉터가 요청마다 부른다. */
@Service
public class CheckServiceAccessService implements CheckServiceAccessUseCase {

    private final ProfileRepository profiles;
    private final CurrentTermsPort currentTerms;

    public CheckServiceAccessService(ProfileRepository profiles, CurrentTermsPort currentTerms) {
        this.profiles = profiles;
        this.currentTerms = currentTerms;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canUseService(CheckServiceAccessQuery query) {
        return profiles.findByAccountId(new AccountId(query.accountId()))
                .map(profile -> profile.isUsable(currentTerms.currentVersions()))
                .orElse(false);
    }
}
