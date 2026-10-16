package com.orbit.schedule.application.port.in.query;

import com.orbit.schedule.application.port.in.query.dto.GetWithdrawalBlockingWorksQuery;
import com.orbit.schedule.application.port.in.query.dto.WithdrawalBlockingWorksInfo;

public interface GetWithdrawalBlockingWorksUseCase {

    WithdrawalBlockingWorksInfo get(GetWithdrawalBlockingWorksQuery query);
}
