package com.orbit.organization.application.port.in.query;

import com.orbit.organization.application.port.in.query.dto.GetWithdrawalBlockingOwnershipsQuery;
import com.orbit.organization.application.port.in.query.dto.WithdrawalBlockingOwnershipsInfo;

public interface GetWithdrawalBlockingOwnershipsUseCase {

    WithdrawalBlockingOwnershipsInfo get(GetWithdrawalBlockingOwnershipsQuery query);
}
