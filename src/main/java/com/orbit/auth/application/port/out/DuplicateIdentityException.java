package com.orbit.auth.application.port.out;

import com.orbit.auth.domain.ExternalIdentity;

/**
 * 같은 외부 식별로 이미 계정이 저장돼 있어 새 계정을 만들 수 없을 때 저장소가 던진다. 동시 첫 로그인의 패자가 받는다. 미처리 예외 로그에 남지 않도록 메시지에는
 * 제공자만 넣고 회원번호는 {@link #identity()}로만 전달한다.
 */
public class DuplicateIdentityException extends RuntimeException {

    private final transient ExternalIdentity identity;

    public DuplicateIdentityException(ExternalIdentity identity, Throwable cause) {
        super("Account already exists for provider " + identity.provider(), cause);
        this.identity = identity;
    }

    public ExternalIdentity identity() {
        return identity;
    }
}
