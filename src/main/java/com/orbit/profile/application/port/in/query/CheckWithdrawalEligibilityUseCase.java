package com.orbit.profile.application.port.in.query;

import com.orbit.profile.application.port.in.query.dto.CheckWithdrawalEligibilityQuery;
import com.orbit.profile.application.port.in.query.dto.WithdrawalEligibilityInfo;

public interface CheckWithdrawalEligibilityUseCase {

    WithdrawalEligibilityInfo check(CheckWithdrawalEligibilityQuery query);
}
