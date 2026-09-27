package com.orbit.auth.application.port.out;

import com.orbit.auth.domain.ExternalIdentity;

/** 같은 외부 식별로 이미 계정이 저장돼 있어 새 계정을 만들 수 없을 때 저장소가 던진다. 동시 첫 로그인의 패자가 받는다. */
public class DuplicateIdentityException extends RuntimeException {

    private final transient ExternalIdentity identity;

    public DuplicateIdentityException(ExternalIdentity identity, Throwable cause) {
        super("Account already exists for " + identity, cause);
        this.identity = identity;
    }

    public ExternalIdentity identity() {
        return identity;
    }
}
