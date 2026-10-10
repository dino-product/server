package com.orbit.profile.application.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.profile.application.error.ProfileErrorCode;
import com.orbit.profile.application.port.in.command.AgreeToTermsUseCase;
import com.orbit.profile.application.port.in.command.dto.AgreeToTermsCommand;
import com.orbit.profile.application.port.out.CurrentTermsPort;
import com.orbit.profile.application.port.out.ProfileRepository;
import com.orbit.profile.domain.AccountId;
import com.orbit.profile.domain.Profile;
import com.orbit.profile.domain.TermsVersions;
import com.orbit.shared.error.BusinessException;

/**
 * 프로필을 입력한 계정의 약관 동의를 기록한다. 가입 미완료 계정은 가입 완료가 되고, 필수 약관이 개정된 활성 계정은 재동의가 된다. 마케팅 값을 보내지 않으면
 * 기존 값을 그대로 둔다(처음 가입이면 미동의).
 */
@Service
public class AgreeToTermsService implements AgreeToTermsUseCase {

    private final ProfileRepository profiles;
    private final CurrentTermsPort currentTerms;
    private final Clock clock;

    public AgreeToTermsService(ProfileRepository profiles, CurrentTermsPort currentTerms, Clock clock) {
        this.profiles = profiles;
        this.currentTerms = currentTerms;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void agree(AgreeToTermsCommand command) {
        AccountId accountId = ProfileOwnership.requireSelf(command.requesterAccountId(), command.accountId());
        if (!command.serviceTermsAgreed() || !command.privacyPolicyAgreed()) {
            throw new BusinessException(ProfileErrorCode.REQUIRED_TERMS_NOT_AGREED);
        }
        Profile profile = profiles.findByAccountId(accountId)
                .orElseThrow(() -> new BusinessException(ProfileErrorCode.PROFILE_REQUIRED));
        TermsVersions current = currentTerms.currentVersions();
        Instant now = clock.instant();
        profile.agreeToTerms(current, now);
        if (command.marketingAgreed() != null) {
            profile.changeMarketingConsent(command.marketingAgreed(), current, now);
        }
        profiles.save(profile);
    }
}
