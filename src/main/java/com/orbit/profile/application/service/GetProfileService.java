package com.orbit.profile.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.profile.application.port.in.query.GetProfileUseCase;
import com.orbit.profile.application.port.in.query.dto.GetProfileQuery;
import com.orbit.profile.application.port.in.query.dto.ProfileInfo;
import com.orbit.profile.application.port.out.CurrentTermsPort;
import com.orbit.profile.application.port.out.ProfileRepository;
import com.orbit.profile.domain.AccountId;
import com.orbit.profile.domain.MarketingConsent;
import com.orbit.profile.domain.Profile;
import com.orbit.profile.domain.SignupStatus;
import com.orbit.profile.domain.SignupStep;

/** 프로필이 없는 계정은 카카오 인증만 마친 가입 미완료 계정으로 보고 프로필 입력 단계를 돌려준다. */
@Service
public class GetProfileService implements GetProfileUseCase {

    private final ProfileRepository profiles;
    private final CurrentTermsPort currentTerms;

    public GetProfileService(ProfileRepository profiles, CurrentTermsPort currentTerms) {
        this.profiles = profiles;
        this.currentTerms = currentTerms;
    }

    @Override
    @Transactional(readOnly = true)
    public ProfileInfo getProfile(GetProfileQuery query) {
        AccountId accountId = ProfileOwnership.requireSelf(query.requesterAccountId(), query.accountId());
        return profiles.findByAccountId(accountId)
                .map(this::toInfo)
                .orElseGet(() -> new ProfileInfo(
                        accountId.value(), SignupStatus.PENDING_SIGNUP, SignupStep.PROFILE, null, null, null, null));
    }

    private ProfileInfo toInfo(Profile profile) {
        return new ProfileInfo(
                profile.accountId().value(),
                profile.status(),
                profile.nextStep(currentTerms.currentVersions()),
                profile.name().value(),
                profile.phoneNumber().value(),
                profile.signedUpAt().orElse(null),
                profile.marketingConsent().map(MarketingConsent::agreed).orElse(null));
    }
}
