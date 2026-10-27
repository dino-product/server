package com.orbit.profile.application.port.out;

import com.orbit.profile.domain.AccountId;

public class ConcurrentProfileUpdateException extends RuntimeException {

    public ConcurrentProfileUpdateException(AccountId accountId, Throwable cause) {
        super("profile changed concurrently: accountId=" + accountId.value(), cause);
    }
}
