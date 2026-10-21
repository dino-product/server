package com.orbit.profile.application.service;

import com.orbit.profile.application.error.ProfileErrorCode;
import com.orbit.profile.domain.AccountId;
import com.orbit.shared.error.BusinessException;

/** 프로필은 본인만 다룬다. 다른 계정의 프로필은 존재 여부를 드러내지 않도록 없는 것과 같은 오류로 거부한다. */
final class ProfileOwnership {

    private ProfileOwnership() {}

    static AccountId requireSelf(Long requesterAccountId, Long accountId) {
        if (requesterAccountId == null || !requesterAccountId.equals(accountId)) {
            throw new BusinessException(ProfileErrorCode.PROFILE_NOT_FOUND);
        }
        return new AccountId(accountId);
    }
}
