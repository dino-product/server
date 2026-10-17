package com.orbit.profile.application.service;

import java.time.Clock;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.profile.application.error.ProfileErrorCode;
import com.orbit.profile.application.port.in.command.ChangeMarketingConsentUseCase;
import com.orbit.profile.application.port.in.command.dto.ChangeMarketingConsentCommand;
import com.orbit.profile.application.port.out.ConcurrentProfileUpdateException;
import com.orbit.profile.application.port.out.CurrentTermsPort;
import com.orbit.profile.application.port.out.ProfileRepository;
import com.orbit.profile.domain.AccountId;
import com.orbit.profile.domain.Profile;
import com.orbit.profile.domain.SignupStatus;
import com.orbit.shared.error.BusinessException;
import com.orbit.shared.error.CommonErrorCode;

/**
 * 가입을 마친 계정의 마케팅 수신 동의를 바꾼다([조직·계정] 정책 §5.1 마이페이지). 가입 전에는 약관 동의 단계에서 함께 받으므로 따로 바꿀 수 없다. 필수 약관
 * 재동의 전인 활성 계정은 바꿀 수 있다 — 약관 동의 범위의 일이라 공통 검사 허용 범위와 같다.
 */
@Service
public class ChangeMarketingConsentService implements ChangeMarketingConsentUseCase {

    private final ProfileRepository profiles;
    private final CurrentTermsPort currentTerms;
    private final Clock clock;

    public ChangeMarketingConsentService(ProfileRepository profiles, CurrentTermsPort currentTerms, Clock clock) {
        this.profiles = profiles;
        this.currentTerms = currentTerms;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void change(ChangeMarketingConsentCommand command) {
        AccountId accountId = ProfileOwnership.requireSelf(command.requesterAccountId(), command.accountId());
        if (command.agreed() == null) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST);
        }
        Profile profile = profiles.findByAccountId(accountId)
                .filter(found -> found.status() == SignupStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ProfileErrorCode.SIGNUP_NOT_COMPLETED));
        profile.changeMarketingConsent(command.agreed(), currentTerms.currentVersions(), clock.instant());
        try {
            profiles.save(profile);
        } catch (ConcurrentProfileUpdateException exception) {
            throw new BusinessException(CommonErrorCode.CONFLICT, exception);
        }
    }
}
