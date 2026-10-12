package com.orbit.organization.domain;

/** 비활성 소속을 총관리자로 지정하려 할 때(O-13). 재활성화한 뒤 지정해야 한다. */
public final class InactiveMembershipException extends IllegalStateException {

    public InactiveMembershipException() {
        super("only an active membership can be designated as owner");
    }
}
