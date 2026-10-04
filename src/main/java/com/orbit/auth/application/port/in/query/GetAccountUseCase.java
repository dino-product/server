package com.orbit.auth.application.port.in.query;

import com.orbit.auth.application.port.in.query.dto.AccountInfo;
import com.orbit.auth.application.port.in.query.dto.GetAccountQuery;

public interface GetAccountUseCase {

    AccountInfo getAccount(GetAccountQuery query);
}
