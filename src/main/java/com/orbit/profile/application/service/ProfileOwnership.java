package com.orbit.profile.application.service;

import com.orbit.profile.application.error.ProfileErrorCode;
import com.orbit.profile.domain.AccountId;
import com.orbit.profile.domain.Profile;
import com.orbit.shared.error.BusinessException;

/**
 * 프로필은 본인만 다룬다. 다른 계정의 프로필은 존재 여부를 드러내지 않도록 없는 것과 같은 오류로 거부한다. 탈퇴한 프로필도 없는 것과 같다 — 탈퇴하면 토큰이
 * 폐기되므로 보통은 닿지 않지만, 폐기가 확정되기 전의 요청이 탈퇴한 계정을 되살리지 않게 한다.
 */
final class ProfileOwnership {

    private ProfileOwnership() {}

    static AccountId requireSelf(Long requesterAccountId, Long accountId) {
        if (requesterAccountId == null || !requesterAccountId.equals(accountId)) {
            throw new BusinessException(ProfileErrorCode.PROFILE_NOT_FOUND);
        }
        return new AccountId(accountId);
    }

    static Profile requireNotWithdrawn(Profile profile) {
        if (profile.isWithdrawn()) {
            throw new BusinessException(ProfileErrorCode.PROFILE_NOT_FOUND);
        }
        return profile;
    }
}
